package com.neuralroots.kosh.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.neuralroots.kosh.MainActivity
import com.neuralroots.kosh.R
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.data.db.DetectedTxn
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.util.Money

/** Shared helpers: permissions, account matching and the "transaction detected" notification. */
object SmsDetector {
    const val CHANNEL = "detected"

    fun hasSmsPermissions(ctx: Context): Boolean =
        listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS).all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }

    fun notificationId(detectedId: Long) = 50_000 + (detectedId % 100_000).toInt()

    /** Account matched by last 4 digits, else the default account, else the first active account. */
    suspend fun accountFor(ctx: Context, d: DetectedTxn): Account? {
        val c = ctx.container
        c.repo.accountForLast4(d.accountLast4)?.let { return it }
        val accounts = c.repo.accountDao.active()
        val def = c.settings.current().defaultAccountId
        return accounts.firstOrNull { it.id == def } ?: accounts.firstOrNull()
    }

    fun headline(d: DetectedTxn): String {
        val amt = Money.format(d.amount)
        val who = d.merchant.ifBlank { null }
        return if (d.isDebit) (if (who != null) "$amt spent at $who" else "$amt debited")
        else (if (who != null) "$amt received from $who" else "$amt credited")
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Detected transactions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Bank and UPI transactions found in your SMS"
            },
        )
    }

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Posts the review notification for a newly detected transaction. */
    @SuppressLint("MissingPermission")
    suspend fun notifyDetected(ctx: Context, id: Long, d: DetectedTxn) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val c = ctx.container
        val type = if (d.isDebit) TxType.EXPENSE else TxType.INCOME
        val catId = c.repo.suggestCategory(d.merchant, type)
        val cat = catId?.let { c.repo.categoryDao.get(it) }
        val account = accountFor(ctx, d)
        val already = c.repo.looksAlreadyLogged(d)
        val nid = notificationId(id)

        val text = buildList {
            val bank = SmsParser.bankName(d.sender)
            add(if (d.accountLast4.isNotEmpty()) "$bank ••${d.accountLast4}" else bank)
            if (cat != null) add("${cat.emoji} ${cat.name}?")
            if (already) add("Possibly already logged")
        }.joinToString(" · ")

        val review = PendingIntent.getActivity(
            ctx, nid, MainActivity.intent(ctx, Routes.SMS_INBOX),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kosh)
            .setContentTitle(headline(d))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nTap to review and categorise."))
            .setContentIntent(review)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (cat != null && account != null && !already) {
            b.addAction(0, "Add as ${cat.name}", SmsActionReceiver.pending(ctx, SmsActionReceiver.ACTION_ADD, id, nid, account.id, cat.id))
        }
        b.addAction(0, "Review", review)
        b.addAction(0, "Ignore", SmsActionReceiver.pending(ctx, SmsActionReceiver.ACTION_IGNORE, id, nid, -1, -1))
        NotificationManagerCompat.from(ctx).notify(nid, b.build())
    }

    /** Category for a detection: learned from this merchant, else keyword guess, else "Other". */
    suspend fun categoryFor(ctx: Context, d: DetectedTxn): com.neuralroots.kosh.data.db.Category? {
        val repo = ctx.container.repo
        val type = if (d.isDebit) TxType.EXPENSE else TxType.INCOME
        val kind = if (d.isDebit) com.neuralroots.kosh.data.db.CategoryKind.EXPENSE else com.neuralroots.kosh.data.db.CategoryKind.INCOME
        repo.suggestCategory(d.merchant, type)?.let { id -> repo.categoryDao.get(id)?.let { return it } }
        val name = CategoryGuess.guess(d.merchant, d.body, kind) ?: CategoryGuess.fallback(kind)
        return repo.categoryDao.findByName(name, kind)?.takeIf { !it.archived }
            ?: repo.categoryDao.byKind(kind).firstOrNull { it.name == CategoryGuess.fallback(kind) }
    }

    /**
     * Handles a fresh detection: records it automatically when auto-add is on and it does not look
     * like a duplicate, otherwise posts the review notification.
     */
    suspend fun handle(ctx: Context, id: Long, d: DetectedTxn) {
        val c = ctx.container
        val s = c.settings.current()
        if (s.smsAutoAdd && !c.repo.looksAlreadyLogged(d)) {
            val account = accountFor(ctx, d)
            if (account != null) {
                val cat = categoryFor(ctx, d)
                val txnId = c.repo.acceptDetected(id, account.id, cat?.id, d.merchant)
                if (txnId != null) {
                    notifyAdded(ctx, id, txnId, d, cat, account)
                    return
                }
            }
        }
        notifyDetected(ctx, id, d)
    }

    @SuppressLint("MissingPermission")
    private fun notifyAdded(
        ctx: Context, id: Long, txnId: Long, d: DetectedTxn,
        cat: com.neuralroots.kosh.data.db.Category?, account: Account,
    ) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val nid = notificationId(id)
        val title = (if (d.isDebit) "−" else "+") + Money.format(d.amount) +
            (d.merchant.ifBlank { null }?.let { " · $it" } ?: "")
        val text = listOfNotNull(cat?.let { "${it.emoji} ${it.name}" }, account.name).joinToString(" · ") + " · recorded"
        val change = PendingIntent.getActivity(
            ctx, nid, MainActivity.intent(ctx, Routes.txnEdit(id = txnId)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kosh)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(change)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, "Change", change)
            .addAction(0, "Undo", SmsActionReceiver.pending(ctx, SmsActionReceiver.ACTION_UNDO, id, nid, txnId, -1))
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, n)
    }

    @SuppressLint("MissingPermission")
    fun notifySimple(ctx: Context, nid: Int, title: String, text: String) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kosh)
            .setContentTitle(title)
            .setContentText(text)
            .setTimeoutAfter(6_000)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(nid, n)
    }
}
