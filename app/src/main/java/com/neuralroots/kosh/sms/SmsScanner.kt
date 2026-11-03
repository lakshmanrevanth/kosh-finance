package com.neuralroots.kosh.sms

import android.content.Context
import android.provider.Telephony
import com.neuralroots.kosh.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads past SMS from the inbox and stores any bank / UPI transactions found (no notifications). */
object SmsScanner {
    data class Result(val scanned: Int, val found: Int)

    suspend fun scan(context: Context, days: Int = 30): Result = withContext(Dispatchers.IO) {
        if (!SmsDetector.hasSmsPermissions(context)) return@withContext Result(0, 0)
        val since = System.currentTimeMillis() - days * 86_400_000L
        val repo = context.container.repo
        var scanned = 0
        var found = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                scanned++
                val sender = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                val d = SmsParser.parse(sender, body, c.getLong(iDate)) ?: continue
                // Skip ones you've clearly already logged by hand.
                if (repo.looksAlreadyLogged(d)) continue
                if (repo.addDetected(d) != null) found++
            }
        }
        Result(scanned, found)
    }
}
