package com.neuralroots.kosh.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.SyncEntity
import org.json.JSONArray
import org.json.JSONObject

/** Drains the outbox to Google Sheets in batches. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val settings = c.settings.current()
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!settings.syncConfigured || (!settings.autoSync && !force)) return Result.success()

        val outbox = c.repo.outboxDao
        var rounds = 0
        while (rounds++ < 200) {
            val items = outbox.pending(BATCH)
            if (items.isEmpty()) break
            val builder = SheetRowBuilder(c.repo)
            val ops = JSONArray()
            val seen = HashSet<String>()
            var hasTxn = false
            for (item in items) {
                val row = if (item.op == SyncEntity.OP_UPSERT) builder.row(item.entity, item.uuid) else null
                ops.put(op(item.entity, item.uuid, row))
                seen += item.entity + "/" + item.uuid
                if (item.entity == SyncEntity.TXN) hasTxn = true
            }
            if (hasTxn) {
                for (uuid in builder.allAccountUuids()) {
                    if (seen.add(SyncEntity.ACCOUNT + "/" + uuid)) {
                        ops.put(op(SyncEntity.ACCOUNT, uuid, builder.row(SyncEntity.ACCOUNT, uuid)))
                    }
                }
            }
            val ids = items.map { it.id }
            try {
                val res = SheetsClient.batch(settings.sheetUrl, settings.sheetToken, ops)
                outbox.remove(ids)
                res.optString("spreadsheetUrl").takeIf { it.isNotBlank() && it != settings.spreadsheetUrl }
                    ?.let { c.settings.setSpreadsheetUrl(it) }
            } catch (e: Exception) {
                val msg = e.message ?: "Sync failed"
                outbox.markFailed(ids, msg)
                c.settings.setSyncResult(System.currentTimeMillis(), msg)
                return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        }
        c.settings.setSyncResult(System.currentTimeMillis(), "")
        return Result.success()
    }

    private fun op(entity: String, uuid: String, row: Map<String, Any?>?): JSONObject {
        val o = JSONObject().put("sheet", entity).put("id", uuid)
        if (row == null) {
            o.put("op", SyncEntity.OP_DELETE)
        } else {
            val r = JSONObject()
            row.forEach { (k, v) -> r.put(k, v ?: "") }
            o.put("op", SyncEntity.OP_UPSERT).put("row", r)
        }
        return o
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val BATCH = 150
        private const val MAX_ATTEMPTS = 6
    }
}
