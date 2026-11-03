package com.neuralroots.kosh.budget

import android.Manifest
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neuralroots.kosh.MainActivity
import com.neuralroots.kosh.R
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Evening "did you log today's spending?" reminder. */
object DailyLogScheduler {
    private const val WORK = "kosh-daily-log"

    fun schedule(context: Context, replace: Boolean = false) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            val s = app.container.settings.current()
            val wm = WorkManager.getInstance(app)
            if (!s.dailyLogReminder) {
                wm.cancelUniqueWork(WORK)
                return@launch
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(s.dailyLogHour.coerceIn(0, 23), 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = java.time.Duration.between(now, next).toMillis()
            val req = PeriodicWorkRequestBuilder<DailyLogWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}

class DailyLogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val c = ctx.container
        if (!c.settings.current().dailyLogReminder) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val now = System.currentTimeMillis()
        val from = Dates.startOfDay(now)
        val to = Dates.endOfDay(now)
        val rows = c.repo.observeTxns(from = from, to = to, type = TxType.EXPENSE).first()
        val spent = rows.sumOf { it.txn.amount }
        val (title, text) = if (rows.isEmpty())
            "Nothing logged today" to "Add today's spends so your budget stays accurate."
        else
            "Today: ${Money.format(spent)} spent" to "${rows.size} ${if (rows.size == 1) "entry" else "entries"} logged. Anything missing?"

        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily logging reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "An evening nudge to log the day's spending"
                }
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 7_500, MainActivity.intent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val add = PendingIntent.getActivity(
            ctx, 7_501,
            Intent().setClassName(ctx, "com.neuralroots.kosh.widget.QuickAddActivity")
                .putExtra("type", TxType.EXPENSE.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kosh)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Add expense", add)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7_502, n) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "daily_log"
    }
}
