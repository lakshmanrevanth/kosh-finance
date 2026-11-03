package com.neuralroots.kosh.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuralroots.kosh.container
import com.neuralroots.kosh.util.Dates

class RecurringWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val repo = ctx.container.repo
        RecurringNotifications.ensureChannel(ctx)

        // (a) Auto-add everything due today or earlier.
        val posted = runCatching { repo.processDueRecurring() }.getOrDefault(emptyList())
        if (posted.isNotEmpty()) RecurringNotifications.autoAdded(ctx, posted)

        // (b) Remind about confirm-style rules that are coming up or overdue.
        val todayStart = Dates.startOfDay(System.currentTimeMillis())
        repo.recurringDao.all()
            .filter { it.active && !it.autoAdd }
            .forEach { r ->
                val days = Dates.daysFromToday(r.nextDate)
                val inWindow = r.nextDate < todayStart || days <= r.remindDaysBefore
                if (inWindow && !RecurringNotifications.alreadyNotified(ctx, r.id, r.nextDate)) {
                    RecurringNotifications.due(ctx, r)
                    RecurringNotifications.markNotified(ctx, r.id, r.nextDate)
                }
            }
        return Result.success()
    }
}
