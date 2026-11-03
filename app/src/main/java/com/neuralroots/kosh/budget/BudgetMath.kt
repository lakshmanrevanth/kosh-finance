package com.neuralroots.kosh.budget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import java.time.YearMonth

/** Derived budget numbers for a month. */
data class BudgetStatus(
    val spent: Long,
    val budget: Long,
    /** spent / budget (can exceed 1). */
    val fraction: Float,
    /** budget − spent (negative when over). */
    val left: Long,
    /** Days remaining in the month including today (0 for past months). */
    val daysLeft: Int,
    /** Safe to spend per remaining day (0 when over budget or month over). */
    val perDay: Long,
    /** Linear projection of month-end spend from the pace so far. */
    val projected: Long,
)

object BudgetMath {
    fun of(spent: Long, budget: Long, month: YearMonth = Dates.currentMonth()): BudgetStatus {
        val now = Dates.currentMonth()
        val days = month.lengthOfMonth()
        val (elapsed, left) = when {
            month < now -> days to 0
            month > now -> 0 to days
            else -> {
                val d = Dates.today().dayOfMonth
                d to (days - d + 1)
            }
        }
        val remaining = budget - spent
        return BudgetStatus(
            spent = spent,
            budget = budget,
            fraction = if (budget > 0) spent.toFloat() / budget else 0f,
            left = remaining,
            daysLeft = left,
            perDay = if (remaining > 0 && left > 0) remaining / left else 0,
            projected = if (elapsed > 0) spent * days / elapsed else spent,
        )
    }

    @Composable
    fun color(fraction: Float): Color {
        val k = KoshTheme.colors
        return when {
            fraction > 1f -> k.expense
            fraction > 0.8f -> k.warning
            else -> k.income
        }
    }
}
