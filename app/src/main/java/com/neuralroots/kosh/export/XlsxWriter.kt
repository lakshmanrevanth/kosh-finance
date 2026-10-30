package com.neuralroots.kosh.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal dependency-free .xlsx (Office Open XML) writer.
 * Strings are inline, Double values use a "#,##0.00" format, Int/Long are plain numbers.
 * Each sheet gets a bold frozen header row and sensible column widths.
 */
object XlsxWriter {
    data class Sheet(val name: String, val headers: List<String>, val rows: List<List<Any?>>)

    private const val STYLE_HEADER = 1
    private const val STYLE_MONEY = 2

    fun write(out: OutputStream, sheets: List<Sheet>) {
        require(sheets.isNotEmpty()) { "Nothing to export" }
        val names = uniqueNames(sheets.map { it.name })
        ZipOutputStream(out).use { zip ->
            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(names))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    private fun uniqueNames(raw: List<String>): List<String> {
        val used = HashSet<String>()
        return raw.map { n ->
            var base = n.replace(Regex("""[\[\]:*?/\\]"""), " ").trim().ifEmpty { "Sheet" }.take(31)
            var candidate = base
            var k = 2
            while (!used.add(candidate.lowercase())) {
                val suffix = " ($k)"; k++
                candidate = base.take(31 - suffix.length) + suffix
            }
            candidate
        }
    }

    private fun contentTypes(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, n -> append("""<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FF1E1B6B"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFEEF0FF"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="3">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun sheetXml(s: Sheet): String {
        val widths = s.headers.indices.map { c ->
            val maxLen = (listOf(s.headers[c].length) + s.rows.take(300).map { r -> display(r.getOrNull(c)).length }).max()
            (maxLen + 2).coerceIn(8, 48)
        }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
            append("<cols>")
            widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols><sheetData>")
            append("""<row r="1">""")
            s.headers.forEachIndexed { c, h -> append(strCell(ref(c, 1), h, STYLE_HEADER)) }
            append("</row>")
            s.rows.forEachIndexed { i, row ->
                val r = i + 2
                append("""<row r="$r">""")
                row.forEachIndexed { c, v ->
                    val ref = ref(c, r)
                    when (v) {
                        null -> {}
                        is Double -> if (v.isFinite()) append("""<c r="$ref" s="$STYLE_MONEY"><v>$v</v></c>""")
                        is Float -> append("""<c r="$ref" s="$STYLE_MONEY"><v>$v</v></c>""")
                        is Int, is Long -> append("""<c r="$ref"><v>$v</v></c>""")
                        is Boolean -> append("""<c r="$ref" t="b"><v>${if (v) 1 else 0}</v></c>""")
                        else -> v.toString().takeIf { it.isNotEmpty() }?.let { append(strCell(ref, it, 0)) }
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
    }

    private fun display(v: Any?): String = when (v) {
        null -> ""
        is Double -> String.format(java.util.Locale.US, "%,.2f", v)
        else -> v.toString()
    }

    private fun strCell(ref: String, text: String, style: Int): String {
        val s = if (style != 0) """ s="$style"""" else ""
        return """<c r="$ref" t="inlineStr"$s><is><t xml:space="preserve">${esc(text)}</t></is></c>"""
    }

    private fun ref(col: Int, row: Int): String = colName(col) + row

    private fun colName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val m = (n - 1) % 26
            sb.insert(0, ('A' + m))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** XML-escapes and drops characters that are illegal in XML 1.0. */
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == '&'.code -> sb.append("&amp;")
                cp == '<'.code -> sb.append("&lt;")
                cp == '>'.code -> sb.append("&gt;")
                cp == '"'.code -> sb.append("&quot;")
                cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF ->
                    sb.appendCodePoint(cp)
                else -> {}
            }
        }
        return sb.toString()
    }
}
