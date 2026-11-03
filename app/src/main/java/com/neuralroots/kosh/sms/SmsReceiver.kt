package com.neuralroots.kosh.sms

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.neuralroots.kosh.container
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.launch

/** Listens for incoming SMS and turns bank / UPI alerts into reviewable detections. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        if (msgs.isEmpty()) return
        // Reassemble multi-part messages per sender.
        val bySender = msgs.filterNotNull().groupBy { it.originatingAddress ?: it.displayOriginatingAddress ?: "" }
        val app = context.applicationContext
        val pending = goAsync()
        app.container.scope.launch {
            try {
                if (!app.container.settings.current().smsDetect) return@launch
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.messageBody ?: it.displayMessageBody ?: "" }
                    val ts = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
                    val d = SmsParser.parse(sender, body, ts) ?: continue
                    val id = app.container.repo.addDetected(d) ?: continue
                    SmsDetector.handle(app, id, d)
                }
            } catch (t: Throwable) {
                Log.w("KoshSms", "SMS handling failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Notification actions: add a detection with the suggested category, or ignore it. */
class SmsActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1).takeIf { it > 0 } ?: return
        val nid = intent.getIntExtra(EXTRA_NID, 0)
        val app = context.applicationContext
        val pending = goAsync()
        app.container.scope.launch {
            try {
                val repo = app.container.repo
                when (intent.action) {
                    ACTION_ADD -> {
                        val d = repo.detectedDao.get(id) ?: return@launch
                        val acc = intent.getLongExtra(EXTRA_ACCOUNT, -1)
                        val cat = intent.getLongExtra(EXTRA_CATEGORY, -1).takeIf { it > 0 }
                        if (acc <= 0) return@launch
                        repo.acceptDetected(id, acc, cat, d.merchant)
                        val catName = cat?.let { repo.categoryDao.get(it)?.name }
                        NotificationManagerCompat.from(app).cancel(nid)
                        SmsDetector.notifySimple(
                            app, nid, "Added ✓",
                            listOfNotNull(Money.format(d.amount), d.merchant.ifBlank { null }, catName).joinToString(" · "),
                        )
                    }
                    ACTION_UNDO -> {
                        // EXTRA_ACCOUNT carries the created transaction id for Undo.
                        val txnId = intent.getLongExtra(EXTRA_ACCOUNT, -1)
                        if (txnId > 0) repo.deleteTxn(txnId)
                        repo.ignoreDetected(id)
                        NotificationManagerCompat.from(app).cancel(nid)
                        SmsDetector.notifySimple(app, nid, "Removed", "The detected transaction was undone.")
                    }
                    ACTION_IGNORE -> {
                        repo.ignoreDetected(id)
                        NotificationManagerCompat.from(app).cancel(nid)
                    }
                }
            } catch (t: Throwable) {
                Log.w("KoshSms", "SMS action failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD = "com.neuralroots.kosh.sms.ADD"
        const val ACTION_IGNORE = "com.neuralroots.kosh.sms.IGNORE"
        const val ACTION_UNDO = "com.neuralroots.kosh.sms.UNDO"
        private const val EXTRA_ID = "detectedId"
        private const val EXTRA_NID = "notificationId"
        private const val EXTRA_ACCOUNT = "accountId"
        private const val EXTRA_CATEGORY = "categoryId"

        fun pending(ctx: Context, action: String, id: Long, nid: Int, accountId: Long, categoryId: Long): PendingIntent {
            val i = Intent(ctx, SmsActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_ID, id); putExtra(EXTRA_NID, nid)
                putExtra(EXTRA_ACCOUNT, accountId); putExtra(EXTRA_CATEGORY, categoryId)
            }
            val req = nid * 4 + when (action) { ACTION_ADD -> 1; ACTION_UNDO -> 3; else -> 2 }
            return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
