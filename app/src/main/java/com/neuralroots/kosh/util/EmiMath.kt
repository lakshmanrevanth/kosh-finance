package com.neuralroots.kosh.util

import com.neuralroots.kosh.data.db.Emi
import com.neuralroots.kosh.data.db.EmiPayment
import com.neuralroots.kosh.data.db.EmiStatus
import kotlin.math.pow
import kotlin.math.roundToLong

data class ScheduleRow(
    val installmentNo: Int,
    val dueDate: Long,
    val emi: Long,
    val interest: Long,
    val principal: Long,
    val balanceAfter: Long,
)

/** Derived, display-ready state of an EMI. */
data class EmiProgress(
    val emi: Emi,
    /** Installments counted as paid (paidBeforeTracking + recorded payments). */
    val paidCount: Int,
    val remainingCount: Int,
    /** Next unpaid installment, or null when finished. */
    val nextInstallment: Int?,
    val nextDueDate: Long?,
    /** Unpaid installments whose due date has passed. */
    val overdueCount: Int,
    val amountPaid: Long,
    val amountRemaining: Long,
    val totalPayable: Long,
    val totalInterest: Long,
    /** Outstanding principal per the amortisation schedule after paid installments. */
    val principalOutstanding: Long,
    val endDate: Long,
) {
    val fraction: Float get() = if (emi.tenureMonths == 0) 0f else paidCount.toFloat() / emi.tenureMonths
    val isClosed: Boolean get() = emi.status == EmiStatus.CLOSED || remainingCount == 0
    val isOverdue: Boolean get() = overdueCount > 0
}

object EmiMath {
    /** Standard reducing-balance EMI. Returns minor units. */
    fun computeEmi(principal: Long, annualRatePct: Double, months: Int): Long {
        if (months <= 0) return 0
        if (annualRatePct <= 0.0) return (principal.toDouble() / months).roundToLong()
        val r = annualRatePct / 12.0 / 100.0
        val f = (1 + r).pow(months)
        return (principal * r * f / (f - 1)).roundToLong()
    }

    fun dueDate(emi: Emi, installmentNo: Int): Long =
        Dates.plusMonths(emi.firstDueDate, installmentNo - 1)

    fun schedule(emi: Emi): List<ScheduleRow> {
        val rows = ArrayList<ScheduleRow>(emi.tenureMonths)
        val r = emi.annualRate / 12.0 / 100.0
        var balance = emi.principal.toDouble()
        for (n in 1..emi.tenureMonths) {
            val interest = if (emi.principal > 0) balance * r else 0.0
            var principalPart = emi.emiAmount - interest
            if (n == emi.tenureMonths || principalPart > balance) principalPart = balance
            if (emi.principal <= 0) principalPart = 0.0
            balance = (balance - principalPart).coerceAtLeast(0.0)
            rows += ScheduleRow(
                installmentNo = n,
                dueDate = dueDate(emi, n),
                emi = emi.emiAmount,
                interest = interest.roundToLong(),
                principal = principalPart.roundToLong(),
                balanceAfter = balance.roundToLong(),
            )
        }
        return rows
    }

    fun progress(emi: Emi, payments: List<EmiPayment>, now: Long = System.currentTimeMillis()): EmiProgress {
        val paidNos = payments.map { it.installmentNo }.toSet()
        val isPaid = { n: Int -> n <= emi.paidBeforeTracking || n in paidNos }
        val paidCount = (1..emi.tenureMonths).count(isPaid)
        val next = (1..emi.tenureMonths).firstOrNull { !isPaid(it) }
        val today = Dates.startOfDay(now)
        val overdue = (1..emi.tenureMonths).count { !isPaid(it) && dueDate(emi, it) < today }
        val total = emi.emiAmount * emi.tenureMonths
        val paidAmt = emi.emiAmount * emi.paidBeforeTracking + payments.sumOf { it.amount }
        val sched = schedule(emi)
        val principalOut = if (emi.principal > 0) {
            if (paidCount == 0) emi.principal else sched.getOrNull(paidCount - 1)?.balanceAfter ?: 0
        } else (total - paidAmt).coerceAtLeast(0)
        return EmiProgress(
            emi = emi,
            paidCount = paidCount,
            remainingCount = emi.tenureMonths - paidCount,
            nextInstallment = next,
            nextDueDate = next?.let { dueDate(emi, it) },
            overdueCount = overdue,
            amountPaid = paidAmt,
            amountRemaining = (emi.emiAmount * (emi.tenureMonths - paidCount)).coerceAtLeast(0),
            totalPayable = total,
            totalInterest = if (emi.principal > 0) (total - emi.principal).coerceAtLeast(0) else 0,
            principalOutstanding = principalOut,
            endDate = dueDate(emi, emi.tenureMonths),
        )
    }

    /** Simple interest accrued on a loan amount from [startMs] to [toMs]. */
    fun simpleInterest(amount: Long, annualRatePct: Double, startMs: Long, toMs: Long): Long {
        if (annualRatePct <= 0.0) return 0
        val days = Dates.daysBetween(startMs, toMs).coerceAtLeast(0)
        return (amount * annualRatePct / 100.0 * days / 365.0).roundToLong()
    }
}
