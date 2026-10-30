package com.neuralroots.kosh.reminder

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
import com.neuralroots.kosh.data.db.Emi
import com.neuralroots.kosh.data.db.LoanDirection
import com.neuralroots.kosh.data.db.LoanWithStats
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_AUTO = "auto"

    fun emiNotificationId(emiId: Long) = 10_000 + emiId.toInt()
    fun loanNotificationId(loanId: Long) = 20_000 + loanId.toInt()
    fun autoNotificationId(emiId: Long) = 30_000 + emiId.toInt()

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "EMI & loan reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Upcoming and overdue EMIs and loans"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_AUTO, "Auto-recorded payments", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Confirmation when an auto-pay EMI is recorded"
            },
        )
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun openRoute(ctx: Context, route: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            ctx, requestCode, MainActivity.intent(ctx, route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, n: NotificationCompat.Builder) {
        if (!canNotify(ctx)) return
        NotificationManagerCompat.from(ctx).notify(id, n.build())
    }

    private fun base(ctx: Context, channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat_kosh)
        .setColor(0xFF5B5BD6.toInt())
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)

    fun emiDue(ctx: Context, emi: Emi, installmentNo: Int, dueDate: Long, overdueCount: Int) {
        val id = emiNotificationId(emi.id)
        val rel = Dates.relativeDue(dueDate).replaceFirstChar { it.lowercase() }
        val title = "${emi.name} EMI ${Money.format(emi.emiAmount)} $rel"
        val text = buildString {
            append("Installment #$installmentNo of ${emi.tenureMonths} · ${Dates.formatDayShort(dueDate)}")
            if (overdueCount > 1) append(" · $overdueCount overdue")
            if (emi.autoPay) append(" · will be auto-recorded")
        }
        val b = base(ctx, CH_REMINDERS)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openRoute(ctx, Routes.emiDetail(emi.id), id))
        if (!emi.autoPay) {
            val action = Intent(ctx, EmiActionReceiver::class.java).apply {
                action = EmiActionReceiver.ACTION_MARK_PAID
                putExtra(EmiActionReceiver.EXTRA_EMI_ID, emi.id)
                putExtra(EmiActionReceiver.EXTRA_INSTALLMENT, installmentNo)
            }
            val pi = PendingIntent.getBroadcast(ctx, id, action, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, "Mark paid", pi)
        }
        post(ctx, id, b)
    }

    fun emiAutoRecorded(ctx: Context, emi: Emi, installments: List<Int>, accountName: String?) {
        val id = autoNotificationId(emi.id)
        val count = installments.size
        val title = if (count == 1) "${emi.name} EMI recorded" else "$count ${emi.name} EMIs recorded"
        val text = "${Money.format(emi.emiAmount * count)} · installment " + installments.joinToString(", ") { "#$it" } +
            (accountName?.let { " · from $it" } ?: "")
        post(
            ctx, id,
            base(ctx, CH_AUTO).setContentTitle(title).setContentText(text)
                .setContentIntent(openRoute(ctx, Routes.emiDetail(emi.id), id)),
        )
    }

    fun emiMarkedPaid(ctx: Context, emi: Emi, installmentNo: Int) {
        val id = emiNotificationId(emi.id)
        post(
            ctx, id,
            base(ctx, CH_AUTO).setContentTitle("Installment #$installmentNo marked paid ✓")
                .setContentText("${emi.name} · ${Money.format(emi.emiAmount)}")
                .setTimeoutAfter(8_000)
                .setContentIntent(openRoute(ctx, Routes.emiDetail(emi.id), id)),
        )
    }

    fun loanDue(ctx: Context, l: LoanWithStats) {
        val due = l.loan.dueDate ?: return
        val id = loanNotificationId(l.loan.id)
        val rel = Dates.relativeDue(due).replaceFirstChar { it.lowercase() }
        val title = if (l.loan.direction == LoanDirection.LENT)
            "${l.personName} owes you ${Money.format(l.outstanding)}"
        else "You owe ${l.personName} ${Money.format(l.outstanding)}"
        post(
            ctx, id,
            base(ctx, CH_REMINDERS).setContentTitle(title).setContentText("Loan $rel · ${Dates.formatDay(due)}")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openRoute(ctx, Routes.loanDetail(l.loan.id), id)),
        )
    }
}
