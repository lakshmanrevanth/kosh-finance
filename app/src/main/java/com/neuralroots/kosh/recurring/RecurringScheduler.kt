package com.neuralroots.kosh.recurring

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Daily (~8 AM) processing of recurring bills: auto-add due ones, remind about the rest. */
object RecurringScheduler {
    private const val DAILY = "kosh-recurring-daily"
    private const val NOW = "kosh-recurring-now"

    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(8, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMin = Duration.between(now, next).toMinutes().coerceAtLeast(1)
        val daily = PeriodicWorkRequestBuilder<RecurringWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMin, TimeUnit.MINUTES)
            .build()
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, daily)
        // Catch up immediately on app start (posts anything due while the phone was off).
        runNow(context)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RecurringWorker>().build(),
        )
    }
}
