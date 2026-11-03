package com.neuralroots.kosh.recurring

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
import com.neuralroots.kosh.data.db.Recurring
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money

object RecurringNotifications {
    const val CHANNEL = "bills"
    private const val PREFS = "recurring_notified"
    private const val AUTO_ID = 49_999

    fun notificationId(ruleId: Long) = 50_000 + ruleId.toInt()

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Bills & recurring", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Upcoming bills, subscriptions and auto-added recurring transactions"
            },
        )
    }

    private fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) {
        if (!canNotify(ctx)) return
        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    private fun open(ctx: Context, requestCode: Int): PendingIntent = PendingIntent.getActivity(
        ctx, requestCode, MainActivity.intent(ctx, Routes.RECURRING),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(ctx: Context) = NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_kosh)
        .setColor(0xFF5B5BD6.toInt())
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)

    // ---- once-per-occurrence bookkeeping
    private fun key(id: Long, date: Long) = "$id@${Dates.formatIsoDay(date)}"
    fun alreadyNotified(ctx: Context, id: Long, date: Long) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(id, date), false)
    fun markNotified(ctx: Context, id: Long, date: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(id, date), true).apply()
    }

    fun due(ctx: Context, r: Recurring) {
        ensureChannel(ctx)
        val id = notificationId(r.id)
        val rel = Dates.relativeDue(r.nextDate).replaceFirstChar { it.lowercase() }
        val verb = if (r.type == TxType.INCOME) "expected" else rel
        val title = if (r.type == TxType.INCOME) "${r.name} ${Money.format(r.amount)} expected ${Dates.formatDayShort(r.nextDate)}"
        else "${r.name} ${Money.format(r.amount)} $verb"
        val text = "${r.frequency.label} · ${Dates.formatDay(r.nextDate)} · tap Paid once it's done"
        fun action(act: String) = PendingIntent.getBroadcast(
            ctx, id * 4 + act.length,
            Intent(ctx, RecurringActionReceiver::class.java).apply {
                action = act
                putExtra(RecurringActionReceiver.EXTRA_ID, r.id)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        post(
            ctx, id,
            base(ctx).setContentTitle(title).setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open(ctx, id))
                .addAction(0, if (r.type == TxType.INCOME) "Received" else "Paid", action(RecurringActionReceiver.ACTION_PAID))
                .addAction(0, "Skip", action(RecurringActionReceiver.ACTION_SKIP)),
        )
    }

    fun autoAdded(ctx: Context, posted: List<Pair<Recurring, Int>>) {
        ensureChannel(ctx)
        val title = if (posted.size == 1) {
            val (r, n) = posted.first()
            "Auto-added: ${r.name} ${Money.format(r.amount * n)}"
        } else "Auto-added ${posted.sumOf { it.second }} recurring transactions"
        val text = posted.joinToString(" · ") { (r, n) -> "${r.name} ${Money.format(r.amount)}" + if (n > 1) " ×$n" else "" }
        post(
            ctx, AUTO_ID,
            base(ctx).setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open(ctx, AUTO_ID)),
        )
    }

    fun recorded(ctx: Context, id: Long, text: String) {
        post(
            ctx, notificationId(id),
            base(ctx).setContentTitle(text).setTimeoutAfter(8_000).setContentIntent(open(ctx, notificationId(id))),
        )
    }
}
