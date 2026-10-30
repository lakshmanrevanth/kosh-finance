package com.neuralroots.kosh.ui.screens.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.util.Dates
import java.time.YearMonth

/** Switch bottom-bar tab (same behaviour as the nav bar). */
fun NavController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** ‹ October 2026 ›. [month] null = all time (only when [allowAllTime]). */
@Composable
fun MonthSwitcher(
    month: YearMonth?,
    onChange: (YearMonth?) -> Unit,
    modifier: Modifier = Modifier,
    allowAllTime: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((month ?: Dates.currentMonth()).minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month")
        }
        Text(
            month?.let { Dates.formatMonth(it) } ?: "All time",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (allowAllTime) {
            TextButton(onClick = { onChange(if (month == null) Dates.currentMonth() else null) }) {
                Text(if (month == null) "This month" else "All time")
            }
        }
        IconButton(
            onClick = { onChange((month ?: Dates.currentMonth()).plusMonths(1)) },
            enabled = month == null || month < Dates.currentMonth().plusMonths(12),
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
    }
}

/** Groups rows by local day (rows already sorted newest first). */
fun groupByDay(rows: List<TxnRow>): List<Pair<Long, List<TxnRow>>> =
    rows.groupBy { Dates.startOfDay(it.txn.date) }.toList()

/** Net effect of a day for display: income − expense (transfers/loans ignored). */
fun dayNet(rows: List<TxnRow>): Long = rows.sumOf {
    when (it.txn.type) {
        TxType.INCOME -> it.txn.amount
        TxType.EXPENSE -> -it.txn.amount
        else -> 0L
    }
}

/** Day net relative to one account: money in minus money out. */
fun accountDayNet(rows: List<TxnRow>, accountId: Long): Long = rows.sumOf { r ->
    val t = r.txn
    when (t.type) {
        TxType.INCOME, TxType.LOAN_IN -> t.amount
        TxType.EXPENSE, TxType.LOAN_OUT -> -t.amount
        TxType.TRANSFER -> if (t.toAccountId == accountId) t.amount else -t.amount
    }
}

@Composable
fun DayHeader(day: Long, net: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            Dates.dayHeader(day), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        if (net != 0L) Text(
            com.neuralroots.kosh.util.Money.formatSigned(net),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
