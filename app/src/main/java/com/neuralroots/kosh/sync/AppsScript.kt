package com.neuralroots.kosh.sync

/**
 * Google Apps Script that receives Kosh's sync batches. Kept identical to apps-script/Code.gs.
 * [script] fills in the user's token so it can be pasted as-is.
 */
object AppsScript {
    const val TOKEN_PLACEHOLDER = "__KOSH_TOKEN__"

    fun script(token: String): String = SOURCE.replace(TOKEN_PLACEHOLDER, token)

    private val SOURCE = """
/**
 * Kosh -> Google Sheets bridge.
 *
 * Setup (the Kosh app walks you through this and copies this script with your token filled in):
 *  1. Create a Google Sheet (sheets.new).
 *  2. Extensions -> Apps Script. Delete the sample code and paste this whole file.
 *  3. Deploy -> New deployment -> type "Web app".
 *     Execute as: Me.  Who has access: Anyone.  Deploy and authorise.
 *  4. Copy the Web app URL (ends in /exec) into Kosh -> Settings -> Google Sheets sync.
 *
 * The app sends batches of row changes. Each sheet has an "ID" column used as the row key.
 */
var TOKEN = '__KOSH_TOKEN__';

function doGet(e) {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  return out({ ok: true, app: 'Kosh', spreadsheetName: ss.getName(), spreadsheetUrl: ss.getUrl() });
}

function doPost(e) {
  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return out({ ok: false, error: 'Invalid request body' });
  }
  if (!body || body.token !== TOKEN) {
    return out({ ok: false, error: 'Invalid token. Copy the script again from the Kosh app, paste it, and redeploy.' });
  }
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  if (body.action === 'ping') {
    return out({ ok: true, applied: 0, spreadsheetName: ss.getName(), spreadsheetUrl: ss.getUrl() });
  }
  var lock = LockService.getScriptLock();
  if (!lock.tryLock(30000)) return out({ ok: false, error: 'Spreadsheet is busy, will retry' });
  try {
    var applied = applyOps(ss, body.ops || []);
    ensureSummary(ss);
    SpreadsheetApp.flush();
    return out({ ok: true, applied: applied, spreadsheetName: ss.getName(), spreadsheetUrl: ss.getUrl() });
  } catch (err) {
    return out({ ok: false, error: String((err && err.message) || err) });
  } finally {
    lock.releaseLock();
  }
}

function out(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

/** Groups ops by sheet; the last op for an ID wins. */
function applyOps(ss, ops) {
  var bySheet = {};
  var order = [];
  for (var i = 0; i < ops.length; i++) {
    var op = ops[i];
    if (!op || !op.sheet || !op.id) continue;
    if (!bySheet[op.sheet]) { bySheet[op.sheet] = {}; order.push(op.sheet); }
    bySheet[op.sheet][op.id] = op;
  }
  var applied = 0;
  for (var j = 0; j < order.length; j++) applied += applySheet(ss, order[j], bySheet[order[j]]);
  return applied;
}

function applySheet(ss, name, opsById) {
  var ids = Object.keys(opsById);
  var upserts = [];
  var deletes = [];
  for (var i = 0; i < ids.length; i++) {
    var op = opsById[ids[i]];
    if (op.op === 'upsert' && op.row) upserts.push(op); else if (op.op === 'delete') deletes.push(op);
  }
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    if (!upserts.length) return deletes.length;
    sheet = ss.insertSheet(name);
  }
  var headers = ensureHeaders(sheet, upserts);
  var width = headers.length;
  var index = idIndex(sheet);
  var appendRows = [];

  for (var u = 0; u < upserts.length; u++) {
    var up = upserts[u];
    var r = index[up.id];
    var values;
    if (r) {
      values = sheet.getRange(r, 1, 1, width).getValues()[0].map(keepCell);
    } else {
      values = [];
      for (var k = 0; k < width; k++) values.push('');
    }
    for (var h = 0; h < width; h++) {
      var key = headers[h];
      if (key === 'ID') values[h] = "'" + up.id;
      else if (Object.prototype.hasOwnProperty.call(up.row, key)) values[h] = cell(up.row[key]);
    }
    if (r) sheet.getRange(r, 1, 1, width).setValues([values]);
    else appendRows.push(values);
  }
  if (appendRows.length) {
    sheet.getRange(sheet.getLastRow() + 1, 1, appendRows.length, width).setValues(appendRows);
  }

  var rows = [];
  for (var d = 0; d < deletes.length; d++) {
    var rowNo = index[deletes[d].id];
    if (rowNo) rows.push(rowNo);
  }
  rows.sort(function (a, b) { return b - a; });
  for (var x = 0; x < rows.length; x++) sheet.deleteRow(rows[x]);
  return upserts.length + deletes.length;
}

/** Ensures a bold, frozen header row with "ID" first and every key used by the upserts. */
function ensureHeaders(sheet, upserts) {
  var lastCol = sheet.getLastColumn();
  var headers = lastCol > 0 ? sheet.getRange(1, 1, 1, lastCol).getDisplayValues()[0] : [];
  while (headers.length && headers[headers.length - 1] === '') headers.pop();
  var changed = false;
  if (!headers.length) { headers = ['ID']; changed = true; }
  for (var i = 0; i < upserts.length; i++) {
    var keys = Object.keys(upserts[i].row);
    for (var k = 0; k < keys.length; k++) {
      if (keys[k] !== 'ID' && headers.indexOf(keys[k]) < 0) { headers.push(keys[k]); changed = true; }
    }
  }
  if (changed) {
    sheet.getRange(1, 1, 1, headers.length).setValues([headers])
      .setFontWeight('bold').setBackground('#EEF0FF').setFontColor('#1E1B6B');
    sheet.setFrozenRows(1);
  }
  return headers;
}

function idIndex(sheet) {
  var map = {};
  var last = sheet.getLastRow();
  if (last < 2) return map;
  var vals = sheet.getRange(2, 1, last - 1, 1).getDisplayValues();
  for (var i = 0; i < vals.length; i++) if (vals[i][0]) map[vals[i][0]] = i + 2;
  return map;
}

var DATE_RE = /^\d{4}-\d{2}-\d{2}( \d{2}:\d{2}(:\d{2})?)?$/;

/** Numbers and booleans stay typed; ISO dates are parsed by Sheets; other text is forced to text. */
function cell(v) {
  if (v === null || v === undefined) return '';
  if (typeof v === 'number' || typeof v === 'boolean') return v;
  var s = String(v);
  if (s === '') return '';
  if (DATE_RE.test(s)) return s;
  return "'" + s;
}

/** Re-writing an existing row: keep text as text so Sheets does not re-parse it. */
function keepCell(v) {
  if (typeof v === 'string' && v !== '') return "'" + v;
  return v;
}

function col(sheetName, header) {
  return 'INDEX(' + sheetName + '!A2:AZ,0,MATCH("' + header + '",' + sheetName + '!1:1,0))';
}

/** Creates the Summary sheet once, after Transactions and Accounts exist. */
function ensureSummary(ss) {
  if (ss.getSheetByName('Summary')) return;
  if (!ss.getSheetByName('Transactions') || !ss.getSheetByName('Accounts')) return;
  var sh = ss.insertSheet('Summary', 0);
  var month = col('Transactions', 'Month');
  var amount = col('Transactions', 'Amount');
  var type = col('Transactions', 'Type');
  var accName = col('Accounts', 'Name');
  var accBal = col('Accounts', 'Current Balance');
  var accArch = col('Accounts', 'Archived');
  var accInc = col('Accounts', 'In Net Worth');

  sh.getRange('A1:D1').setValues([['Month', 'Expense', 'Income', 'Net']]);
  sh.getRange('F1:G1').setValues([['Account', 'Balance']]);
  sh.getRange('I1:I1').setValues([['Net worth']]);
  sh.getRange('K1:K1').setValues([['Spent this month']]);
  sh.getRange('M1:M1').setValues([['Earned this month']]);
  sh.getRange('A1:M1').setFontWeight('bold').setFontColor('#1E1B6B');
  sh.setFrozenRows(1);

  sh.getRange('A2').setFormula('=IFERROR(SORT(UNIQUE(FILTER(' + month + ',' + month + '<>"")),1,FALSE),"")');
  sh.getRange('B2').setFormula('=MAP(A2:A,LAMBDA(m,IF(m="","",SUMIFS(' + amount + ',' + month + ',m,' + type + ',"Expense"))))');
  sh.getRange('C2').setFormula('=MAP(A2:A,LAMBDA(m,IF(m="","",SUMIFS(' + amount + ',' + month + ',m,' + type + ',"Income"))))');
  sh.getRange('D2').setFormula('=MAP(A2:A,B2:B,C2:C,LAMBDA(m,e,i,IF(m="","",i-e)))');
  sh.getRange('F2').setFormula('=IFERROR(FILTER({' + accName + ',' + accBal + '},' + accName + '<>"",' + accArch + '="No"),"")');
  sh.getRange('I2').setFormula('=SUMIFS(' + accBal + ',' + accInc + ',"Yes",' + accArch + ',"No")');
  sh.getRange('K2').setFormula('=SUMIFS(' + amount + ',' + month + ',TEXT(TODAY(),"yyyy-mm"),' + type + ',"Expense")');
  sh.getRange('M2').setFormula('=SUMIFS(' + amount + ',' + month + ',TEXT(TODAY(),"yyyy-mm"),' + type + ',"Income")');

  sh.getRange('B2:D').setNumberFormat('#,##0.00');
  sh.getRange('G2:G').setNumberFormat('#,##0.00');
  sh.getRange('I2:M2').setNumberFormat('#,##0.00');
  sh.setColumnWidth(1, 110);
  sh.setColumnWidth(6, 180);
}
""".trimStart()
}
