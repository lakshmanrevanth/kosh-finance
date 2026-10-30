package com.neuralroots.kosh.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.text.input.KeyboardType
import com.neuralroots.kosh.AppContainer
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.AccountType
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money

// ------------------------------------------------------------------ ViewModels

/** Creates a ViewModel with access to the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> koshViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

// ------------------------------------------------------------------ Scaffolding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KoshTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun KoshCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 48.sp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle.isNotEmpty()) Text(
            subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

// ------------------------------------------------------------------ Money / visuals

@Composable
fun txTypeColor(type: TxType): Color = when (type) {
    TxType.EXPENSE -> KoshTheme.colors.expense
    TxType.INCOME -> KoshTheme.colors.income
    TxType.TRANSFER -> KoshTheme.colors.transfer
    TxType.LOAN_OUT, TxType.LOAN_IN -> KoshTheme.colors.loan
}

/** Signed amount text: expenses/money-out negative, income/money-in positive. */
fun signedAmount(type: TxType, amount: Long): String = when (type) {
    TxType.EXPENSE, TxType.LOAN_OUT -> "−" + Money.format(amount)
    TxType.INCOME, TxType.LOAN_IN -> "+" + Money.format(amount)
    TxType.TRANSFER -> Money.format(amount)
}

@Composable
fun EmojiBubble(emoji: String, color: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.45f).sp) }
}

@Composable
fun IconBubble(icon: ImageVector, color: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f)) }
}

fun accountTypeIcon(type: AccountType): ImageVector = when (type) {
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.SAVINGS -> Icons.Filled.Savings
    AccountType.CASH -> Icons.Filled.Payments
    AccountType.CREDIT_CARD -> Icons.Filled.CreditCard
    AccountType.WALLET -> Icons.Filled.AccountBalanceWallet
    AccountType.INVESTMENT -> Icons.Filled.ShowChart
}

/** Standard transaction row used everywhere a list of transactions is shown. */
@Composable
fun TxnListItem(row: TxnRow, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    val t = row.txn
    val color = txTypeColor(t.type)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (t.type) {
            TxType.TRANSFER -> IconBubble(Icons.Filled.SwapHoriz, color)
            TxType.LOAN_IN, TxType.LOAN_OUT -> EmojiBubble("🤝", color)
            else -> EmojiBubble(row.categoryEmoji ?: "🧾", row.categoryColor?.let { Color(it) } ?: color)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = when (t.type) {
                TxType.TRANSFER -> "${row.accountName ?: "?"} → ${row.toAccountName ?: "?"}"
                else -> t.payee.ifBlank { row.categoryName ?: t.type.label }
            }
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (t.type != TxType.TRANSFER) {
                    if (t.payee.isNotBlank() && row.categoryName != null) add(row.categoryName)
                    row.accountName?.let { add(it) }
                }
                if (t.note.isNotBlank()) add(t.note)
                if (showDate) add(Dates.formatDayShort(t.date)) else add(Dates.formatTime(t.date))
            }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            signedAmount(t.type, t.amount),
            style = MaterialTheme.typography.titleSmall,
            color = if (t.type == TxType.EXPENSE) MaterialTheme.colorScheme.onSurface else color,
        )
    }
}

@Composable
fun RoundedProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp,
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "progress")
    LinearProgressIndicator(
        progress = { anim },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)),
        color = color,
        trackColor = color.copy(alpha = 0.15f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun StatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.10f)).padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

// ------------------------------------------------------------------ Inputs

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Read-only field that opens a Material date picker. [value] is epoch ms; time-of-day is preserved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: Long?,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select date",
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value?.let { Dates.formatDay(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        // Overlay to capture taps (readOnly TextField swallows clicks otherwise).
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).clickable { open = true })
    }
    if (open) {
        // DatePicker works in UTC midnight; convert to/from local dates.
        val initialUtc = value?.let {
            Dates.toLocalDate(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val d = java.time.Instant.ofEpochMilli(utc).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        val keepTime = value ?: System.currentTimeMillis()
                        onChange(Dates.withCurrentTime(Dates.toMs(d), keepTime))
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Dropdown selector. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String,
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    leading: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(labelOf) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = leading,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(labelOf(o)) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

/** Money text field: accepts digits and one decimal point. */
@Composable
fun MoneyField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.isEmpty() || s.matches(Regex("""^\d{0,12}(\.\d{0,2})?$"""))) onChange(s) },
        label = { Text(label) },
        prefix = { Text(Money.symbol + " ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

// ------------------------------------------------------------------ Charts

data class ChartSlice(val value: Float, val color: Color)

@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val sweepAnim by animateFloatAsState(1f, label = "donut")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(thickness / 2)) {
            val stroke = Stroke(width = thickness.toPx(), cap = StrokeCap.Butt)
            drawArc(track, 0f, 360f, false, style = stroke)
            var start = -90f
            val gap = if (slices.size > 1) 1.5f else 0f
            slices.forEach { s ->
                val sweep = 360f * (s.value / total) * sweepAnim
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, style = stroke)
                start += sweep
            }
        }
        center()
    }
}

data class BarPoint(val label: String, val a: Float, val b: Float = 0f)

/** Grouped bar chart: [colorA] bars (e.g. expense) and optional [colorB] bars (e.g. income). */
@Composable
fun BarChart(
    points: List<BarPoint>,
    colorA: Color,
    colorB: Color? = null,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),
    highlightLast: Boolean = true,
) {
    val max = points.maxOfOrNull { maxOf(it.a, it.b) }?.takeIf { it > 0f } ?: 1f
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (points.isEmpty()) return@Canvas
            val slot = size.width / points.size
            val groups = if (colorB != null) 2 else 1
            val barW = (slot * 0.5f / groups).coerceAtMost(28.dp.toPx())
            points.forEachIndexed { i, p ->
                val cx = slot * i + slot / 2
                val alpha = if (!highlightLast || i == points.lastIndex) 1f else 0.55f
                val left = cx - barW * groups / 2
                val ha = size.height * (p.a / max)
                drawRoundRect(
                    colorA.copy(alpha = alpha), Offset(left, size.height - ha), Size(barW, ha),
                    CornerRadius(barW / 3, barW / 3),
                )
                if (colorB != null) {
                    val hb = size.height * (p.b / max)
                    drawRoundRect(
                        colorB.copy(alpha = alpha), Offset(left + barW, size.height - hb), Size(barW, hb),
                        CornerRadius(barW / 3, barW / 3),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            points.forEach {
                Text(
                    it.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
