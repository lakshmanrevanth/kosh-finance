package com.neuralroots.kosh.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neuralroots.kosh.container
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val PERIODIC = "kosh-reminders-daily"
    private const val ONE_TIME = "kosh-reminders-now"
    private const val CATCH_UP = "kosh-autopay-catchup"

    /**
     * Schedules the daily reminder check at settings.reminderHour.
     * [replace] = true re-times an existing schedule (call after the hour changes).
     * Also runs a silent auto-pay catch-up immediately.
     */
    fun schedule(context: Context, replace: Boolean = false) {
        val app = context.applicationContext
        val c = app.container
        c.scope.launch {
            val hour = c.settings.current().reminderHour
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(hour), TimeUnit.MILLISECONDS)
                .build()
            val wm = WorkManager.getInstance(app)
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
            if (!replace) {
                wm.enqueueUniqueWork(
                    CATCH_UP, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInputData(workDataOf(ReminderWorker.KEY_SILENT to true))
                        .build(),
                )
            }
        }
    }

    /** Runs the full check (with notifications) right now. */
    fun runNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ONE_TIME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build(),
        )
    }

    private fun delayUntil(hour: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
