@file:OptIn(ExperimentalMaterial3Api::class)

package com.neuralroots.kosh.ui.screens.emi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neuralroots.kosh.data.db.Account
import com.neuralroots.kosh.ui.components.DateField
import com.neuralroots.kosh.ui.components.MoneyField
import com.neuralroots.kosh.ui.components.SelectField
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money

/** Gradient hero card with white content. Shared by EMI and loan screens. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = KoshTheme.colors
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
                .padding(20.dp),
            content = content,
        )
    }
}

@Composable
fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
    }
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** Circle with the first letter of a name. */
@Composable
fun InitialBubble(name: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** Colour for a due date: overdue -> expense, within [soonDays] -> warning, else muted. */
@Composable
fun dueColor(dueMs: Long, soonDays: Int = 3): Color {
    val d = Dates.daysFromToday(dueMs)
    return when {
        d < 0 -> KoshTheme.colors.expense
        d <= soonDays -> KoshTheme.colors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Dialog to record an EMI installment (or any payment-like entry).
 * [onConfirm] gets amount, date, optional account id, note.
 */
@Composable
fun PaymentDialog(
    title: String,
    subtitle: String?,
    defaultAmount: Long,
    accounts: List<Account>,
    defaultAccountId: Long?,
    recordLabel: String,
    confirmLabel: String,
    defaultRecord: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, date: Long, accountId: Long?, note: String) -> Unit,
) {
    var amount by remember { mutableStateOf(if (defaultAmount > 0) Money.toInput(defaultAmount) else "") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var record by remember { mutableStateOf(defaultRecord && accounts.isNotEmpty()) }
    var account by remember {
        mutableStateOf(accounts.firstOrNull { it.id == defaultAccountId } ?: accounts.firstOrNull())
    }
    var note by remember { mutableStateOf("") }
    val parsed = Money.parse(amount)
    val valid = parsed != null && parsed > 0 && (!record || account != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyField("Amount", amount, { amount = it }, isError = amount.isNotEmpty() && (parsed == null || parsed <= 0))
                DateField("Date", date, { date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(recordLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = record, onCheckedChange = { record = it }, enabled = accounts.isNotEmpty())
                }
                if (record) SelectField("Account", accounts, account, { it.name }, { account = it })
                OutlinedTextField(
                    value = note, onValueChange = { note = it }, label = { Text("Note (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onConfirm(parsed!!, date, if (record) account?.id else null, note.trim())
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
