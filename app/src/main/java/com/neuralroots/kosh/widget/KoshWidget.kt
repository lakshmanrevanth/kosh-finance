package com.neuralroots.kosh.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.neuralroots.kosh.MainActivity
import com.neuralroots.kosh.R
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.Category
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.TxnRow
import com.neuralroots.kosh.data.prefs.ThemeMode
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.first
import androidx.glance.color.ColorProvider as DayNightColor

/** Snapshot of everything the widget shows. */
data class WidgetData(
    val monthSpent: Long = 0,
    val todaySpent: Long = 0,
    val balance: Long? = null,
    val balanceLabel: String = "",
    val categories: List<Category> = emptyList(),
    val recent: List<TxnRow> = emptyList(),
)

suspend fun loadWidgetData(context: Context, cfg: WidgetConfig): WidgetData = runCatching {
    val c = context.container
    val settings = c.settings.current()
    if (Money.currencyCode != settings.currencyCode) Money.currencyCode = settings.currencyCode
    val repo = c.repo
    val month = Dates.monthRange(Dates.currentMonth())
    val now = System.currentTimeMillis()
    val monthSpent = repo.total(TxType.EXPENSE, month.first, month.last)
    val todaySpent = repo.total(TxType.EXPENSE, Dates.startOfDay(now), Dates.endOfDay(now))
    val accounts = repo.accountDao.withBalance()
    val (balance, label) = when (cfg.balanceMode) {
        BalanceMode.NONE -> null to ""
        BalanceMode.TOTAL -> accounts.filter { it.account.includeInTotal }.sumOf { it.balance } to "Net worth"
        BalanceMode.ACCOUNT -> accounts.firstOrNull { it.account.id == cfg.balanceAccountId }
            ?.let { it.balance to it.account.name } ?: (null to "")
    }
    val all = repo.categoryDao.byKind(CategoryKind.EXPENSE)
    val cats = if (cfg.quickCategoryIds.isNotEmpty()) {
        cfg.quickCategoryIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
    } else all.take(4)
    WidgetData(
        monthSpent = monthSpent,
        todaySpent = todaySpent,
        balance = balance,
        balanceLabel = label,
        categories = cats.take(4),
        recent = repo.txnDao.observeRecent(3).first(),
    )
}.getOrDefault(WidgetData())

class KoshWidget : GlanceAppWidget() {
    companion object {
        private val SMALL = DpSize(110.dp, 50.dp)
        private val MEDIUM = DpSize(220.dp, 100.dp)
        private val TALL = DpSize(220.dp, 170.dp)
        private val LARGE = DpSize(220.dp, 260.dp)

        /** Bumped by [WidgetRefresher] to force a reload of data + config. */
        val VERSION = longPreferencesKey("kosh_widget_version")
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, TALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initialCfg = WidgetPrefs.load(context, widgetId)
        val initialData = loadWidgetData(context, initialCfg)
        provideContent {
            val version = currentState<Preferences>()[VERSION] ?: 0L
            val state by produceState(initialValue = initialCfg to initialData, version) {
                if (version != 0L) {
                    val cfg = WidgetPrefs.load(context, widgetId)
                    value = cfg to loadWidgetData(context, cfg)
                }
            }
            WidgetBody(widgetId, state.first, state.second)
        }
    }
}

class KoshWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KoshWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPrefs.delete(context, it) }
    }
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetRefresher.refresh(context, glanceId)
    }
}

object WidgetRefresher {
    /** Reloads every placed widget. Safe to call often; failures are ignored. */
    suspend fun refreshAll(context: Context) {
        runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(KoshWidget::class.java).forEach { refresh(context, it) }
        }
    }

    suspend fun refresh(context: Context, id: GlanceId) {
        runCatching {
            updateAppWidgetState(context, id) { it[KoshWidget.VERSION] = System.currentTimeMillis() }
            KoshWidget().update(context, id)
        }
    }

    suspend fun refreshWidget(context: Context, appWidgetId: Int) {
        runCatching { refresh(context, GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)) }
    }
}

// ------------------------------------------------------------------ UI

private class Palette(
    val bg: ColorProvider,
    val text: ColorProvider,
    val muted: ColorProvider,
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val chip: ColorProvider,
    val chipText: ColorProvider,
    val expense: ColorProvider,
    val income: ColorProvider,
)

private fun palette(cfg: WidgetConfig): Palette {
    val a = (cfg.opacity.coerceIn(40, 100)) / 100f
    fun c(day: Long, night: Long, alpha: Float = 1f): ColorProvider {
        val d = Color(day).copy(alpha = alpha)
        val n = Color(night).copy(alpha = alpha)
        return when (cfg.theme) {
            ThemeMode.LIGHT -> ColorProvider(d)
            ThemeMode.DARK -> ColorProvider(n)
            ThemeMode.SYSTEM -> DayNightColor(day = d, night = n)
        }
    }
    return Palette(
        bg = c(0xFFFFFFFF, 0xFF14151C, a),
        text = c(0xFF101828, 0xFFF2F4F7),
        muted = c(0xFF667085, 0xFF98A2B3),
        primary = c(0xFF5B5BD6, 0xFF8E8EF5),
        onPrimary = c(0xFFFFFFFF, 0xFF15134D),
        chip = c(0xFFEEF0FF, 0xFF262736),
        chipText = c(0xFF3E3EB8, 0xFFC7C7FA),
        expense = c(0xFFD92D20, 0xFFF97066),
        income = c(0xFF079455, 0xFF47CD89),
    )
}

private fun quickAdd(context: Context, widgetId: Int, cfg: WidgetConfig, type: TxType, categoryId: Long? = null) =
    actionStartActivity(
        QuickAddActivity.intent(context, type, categoryId, cfg.defaultAccountId.takeIf { it > 0 }, widgetId),
    )

private fun openConfig(context: Context, widgetId: Int) = actionStartActivity(
    Intent(context, WidgetConfigActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        data = Uri.parse("kosh://widget-config/$widgetId")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    },
)

@Composable
private fun WidgetBody(widgetId: Int, cfg: WidgetConfig, d: WidgetData) {
    val ctx = LocalContext.current
    val size = LocalSize.current
    val p = palette(cfg)
    Box(
        GlanceModifier.fillMaxSize().background(p.bg).cornerRadius(24.dp).padding(12.dp),
    ) {
        if (size.height < 90.dp || size.width < 180.dp) {
            SmallBody(ctx, widgetId, cfg, d, p)
        } else {
            Column(GlanceModifier.fillMaxSize()) {
                Header(ctx, widgetId, p)
                Spacer(GlanceModifier.height(4.dp))
                Stats(cfg, d, p)
                if (size.height >= 160.dp && d.categories.isNotEmpty()) {
                    Spacer(GlanceModifier.height(10.dp))
                    CategoryChips(ctx, widgetId, cfg, d.categories, p)
                }
                if (size.height >= 250.dp && d.recent.isNotEmpty()) {
                    Spacer(GlanceModifier.height(10.dp))
                    Recent(d.recent, p)
                }
                Spacer(GlanceModifier.defaultWeight())
                Buttons(ctx, widgetId, cfg, p)
            }
        }
    }
}

@Composable
private fun SmallBody(ctx: Context, widgetId: Int, cfg: WidgetConfig, d: WidgetData, p: Palette) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.intent(ctx))),
        ) {
            Text("This month", style = TextStyle(color = p.muted, fontSize = 11.sp), maxLines = 1)
            Text(
                Money.compact(d.monthSpent),
                style = TextStyle(color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Box(
            GlanceModifier.size(44.dp).background(p.primary).cornerRadius(22.dp)
                .clickable(quickAdd(ctx, widgetId, cfg, cfg.defaultType)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(R.drawable.ic_widget_add), contentDescription = "Add expense",
                colorFilter = ColorFilter.tint(p.onPrimary), modifier = GlanceModifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun Header(ctx: Context, widgetId: Int, p: Palette) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Kosh",
            style = TextStyle(color = p.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.intent(ctx))),
        )
        Image(
            ImageProvider(R.drawable.ic_widget_refresh), contentDescription = "Refresh",
            colorFilter = ColorFilter.tint(p.muted),
            modifier = GlanceModifier.size(18.dp).clickable(actionRunCallback<RefreshCallback>()),
        )
        Spacer(GlanceModifier.width(12.dp))
        Image(
            ImageProvider(R.drawable.ic_widget_tune), contentDescription = "Widget settings",
            colorFilter = ColorFilter.tint(p.muted),
            modifier = GlanceModifier.size(18.dp).clickable(openConfig(ctx, widgetId)),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: ColorProvider, p: Palette, big: Boolean, modifier: GlanceModifier) {
    Column(modifier) {
        Text(label, style = TextStyle(color = p.muted, fontSize = 11.sp), maxLines = 1)
        Text(
            value,
            style = TextStyle(color = color, fontSize = if (big) 22.sp else 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
private fun Stats(cfg: WidgetConfig, d: WidgetData, p: Palette) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Stat("Spent this month", Money.format(d.monthSpent), p.text, p, big = true, modifier = GlanceModifier.defaultWeight())
        if (cfg.showToday) {
            Stat("Today", Money.compact(d.todaySpent), p.expense, p, big = false, modifier = GlanceModifier.padding(start = 8.dp))
        }
        if (d.balance != null) {
            Stat(
                d.balanceLabel.take(14), Money.compact(d.balance),
                if (d.balance < 0) p.expense else p.income, p, big = false,
                modifier = GlanceModifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun CategoryChips(ctx: Context, widgetId: Int, cfg: WidgetConfig, cats: List<Category>, p: Palette) {
    Row(GlanceModifier.fillMaxWidth()) {
        cats.forEachIndexed { i, c ->
            if (i > 0) Spacer(GlanceModifier.width(6.dp))
            Column(
                GlanceModifier.defaultWeight().height(52.dp).background(p.chip).cornerRadius(14.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clickable(quickAdd(ctx, widgetId, cfg, TxType.EXPENSE, c.id)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.emoji, style = TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center))
                Text(
                    c.name.substringBefore(" &").substringBefore(" /").take(10),
                    style = TextStyle(color = p.chipText, fontSize = 10.sp, textAlign = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Recent(rows: List<TxnRow>, p: Palette) {
    Column(GlanceModifier.fillMaxWidth()) {
        Text("Recent", style = TextStyle(color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium))
        rows.forEach { r ->
            val t = r.txn
            val (sign, color) = when (t.type) {
                TxType.EXPENSE, TxType.LOAN_OUT -> "−" to p.text
                TxType.INCOME, TxType.LOAN_IN -> "+" to p.income
                TxType.TRANSFER -> "" to p.primary
            }
            Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (t.type) {
                        TxType.TRANSFER -> "⇄"
                        TxType.LOAN_IN, TxType.LOAN_OUT -> "🤝"
                        else -> r.categoryEmoji ?: "🧾"
                    },
                    style = TextStyle(fontSize = 13.sp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    t.payee.ifBlank { r.categoryName ?: t.type.label },
                    style = TextStyle(color = p.text, fontSize = 12.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(sign + Money.format(t.amount), style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun Buttons(ctx: Context, widgetId: Int, cfg: WidgetConfig, p: Palette) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.defaultWeight().height(38.dp).background(p.primary).cornerRadius(14.dp)
                .clickable(quickAdd(ctx, widgetId, cfg, TxType.EXPENSE)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Expense", style = TextStyle(color = p.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(6.dp))
        Box(
            GlanceModifier.defaultWeight().height(38.dp).background(p.chip).cornerRadius(14.dp)
                .clickable(quickAdd(ctx, widgetId, cfg, TxType.INCOME)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Income", style = TextStyle(color = p.chipText, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(6.dp))
        Box(
            GlanceModifier.size(38.dp).background(p.chip).cornerRadius(14.dp)
                .clickable(quickAdd(ctx, widgetId, cfg, TxType.TRANSFER)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(R.drawable.ic_widget_swap), contentDescription = "Transfer",
                colorFilter = ColorFilter.tint(p.chipText), modifier = GlanceModifier.size(18.dp),
            )
        }
    }
}
