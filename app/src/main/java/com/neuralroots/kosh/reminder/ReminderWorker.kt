package com.neuralroots.kosh.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.LoanStatus
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.EmiMath

/**
 * Daily check:
 *  - auto-pay EMIs: records every unpaid installment due up to today (only those due on/after the
 *    day the EMI was added, so old history is never back-filled by surprise);
 *  - posts reminders for EMIs due within their reminder window or overdue;
 *  - posts reminders for open loans due within 3 days or overdue.
 * With [KEY_SILENT] (app start) it only does the auto-pay catch-up.
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val c = ctx.container
        val repo = c.repo
        val settings = c.settings.current()
        val silent = inputData.getBoolean(KEY_SILENT, false)
        val notify = settings.remindersEnabled && !silent
        Notifications.ensureChannels(ctx)

        val now = System.currentTimeMillis()
        val todayEnd = Dates.endOfDay(now)
        val accounts = repo.accountDao.all().associateBy { it.id }
        val paymentsByEmi = repo.emiDao.allPayments().groupBy { it.emiId }

        for (emi in repo.emiDao.active()) {
            var payments = paymentsByEmi[emi.id].orEmpty()
            val account = emi.accountId?.let { accounts[it] }
            if (emi.autoPay && account != null) {
                val paidNos = payments.map { it.installmentNo }.toSet()
                val trackingStart = Dates.startOfDay(emi.createdAt)
                val recorded = mutableListOf<Int>()
                for (n in (emi.paidBeforeTracking + 1)..emi.tenureMonths) {
                    if (n in paidNos) continue
                    val due = EmiMath.dueDate(emi, n)
                    if (due > todayEnd) break
                    if (due < trackingStart) continue
                    runCatching { repo.markEmiPaid(emi.id, n, emi.emiAmount, Dates.withCurrentTime(due, now), account.id) }
                        .onSuccess { recorded += n }
                }
                if (recorded.isNotEmpty()) {
                    if (settings.remindersEnabled) Notifications.emiAutoRecorded(ctx, emi, recorded, account.name)
                    payments = repo.emiDao.paymentsFor(emi.id)
                }
            }
            if (!notify) continue
            val p = EmiMath.progress(emi, payments, now)
            val n = p.nextInstallment ?: continue
            val due = p.nextDueDate ?: continue
            if (Dates.daysFromToday(due) <= emi.remindDaysBefore) {
                Notifications.emiDue(ctx, emi, n, due, p.overdueCount)
            }
        }

        if (notify) {
            repo.loanDao.openLoans()
                .filter { it.loan.status == LoanStatus.OPEN && it.outstanding > 0 }
                .forEach { l ->
                    val due = l.loan.dueDate ?: return@forEach
                    if (Dates.daysFromToday(due) <= 3) Notifications.loanDue(ctx, l)
                }
        }
        return Result.success()
    }

    companion object {
        const val KEY_SILENT = "silent"
    }
}
