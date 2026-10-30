package com.neuralroots.kosh.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.neuralroots.kosh.data.db.SyncEntity
import com.neuralroots.kosh.data.repo.FinanceRepository
import com.neuralroots.kosh.sync.SheetHeaders
import com.neuralroots.kosh.sync.SheetRowBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

object Exporter {
    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val CSV_MIME = "text/csv"

    /** Builds the workbook sheets. Transactions are filtered to [from]..[to]; other sheets are complete. */
    suspend fun buildSheets(repo: FinanceRepository, entities: List<String>, from: Long, to: Long): List<XlsxWriter.Sheet> {
        val b = SheetRowBuilder(repo)
        return SheetHeaders.order.filter { it in entities }.map { e ->
            val headers = SheetHeaders.headers.getValue(e)
            val rows = b.rows(e, from, to).map { r -> headers.map { h -> r[h] } }
            XlsxWriter.Sheet(e.replace('_', ' '), headers, rows)
        }
    }

    suspend fun writeXlsx(repo: FinanceRepository, out: OutputStream, entities: List<String>, from: Long, to: Long): Int =
        withContext(Dispatchers.IO) {
            val sheets = buildSheets(repo, entities, from, to)
            XlsxWriter.write(out, sheets)
            sheets.sumOf { it.rows.size }
        }

    /** Transactions as CSV (UTF-8 with BOM so Excel shows ₹ and emoji correctly). */
    suspend fun writeTransactionsCsv(repo: FinanceRepository, out: OutputStream, from: Long, to: Long): Int =
        withContext(Dispatchers.IO) {
            val headers = SheetHeaders.headers.getValue(SyncEntity.TXN)
            val rows = SheetRowBuilder(repo).rows(SyncEntity.TXN, from, to)
            val w = out.bufferedWriter(Charsets.UTF_8)
            w.write("﻿")
            w.write(headers.joinToString(",") { csv(it) }); w.write("\r\n")
            rows.forEach { r -> w.write(headers.joinToString(",") { csv(r[it]) }); w.write("\r\n") }
            w.flush()
            rows.size
        }

    private fun csv(v: Any?): String {
        val s = when (v) {
            null -> ""
            is Double -> String.format(java.util.Locale.US, "%.2f", v)
            else -> v.toString()
        }
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    /** Writes into cache/exports and returns a shareable content Uri. */
    suspend fun cacheFile(context: Context, name: String, write: suspend (OutputStream) -> Unit): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name)
        withContext(Dispatchers.IO) { f.outputStream().use { write(it) } }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    fun shareIntent(uri: Uri, mime: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share export",
        )
}
