package com.neuralroots.kosh.budget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.neuralroots.kosh.MainActivity
import com.neuralroots.kosh.R
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.CategoryKind
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.ui.nav.Routes
import com.neuralroots.kosh.util.Dates
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Posts a notification the first time a budget crosses 80% or 100% in a month — overall and per category.
 * Called after every data change, so it is debounced and cheap.
 */
object BudgetWatcher {
    private const val TAG = "KoshBudget"
    private const val CHANNEL = "budget"
    private const val PREFS = "budget_alerts"
    private val THRESHOLDS = listOf(100, 80)
    private val mutex = Mutex()
    @Volatile private var lastRun = 0L

    suspend fun check(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRun < 1000) return
        lastRun = now
        try {
            mutex.withLock { runCheck(context.applicationContext) }
        } catch (t: Throwable) {
            Log.w(TAG, "budget check failed", t)
        }
    }

    private suspend fun runCheck(ctx: Context) {
        val c = ctx.container
        val settings = c.settings.current()
        if (!settings.budgetAlerts) return
        val month = Dates.currentMonth()
        val range = Dates.monthRange(month)
        val key = month.toString()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (settings.monthlyBudget > 0) {
            val spent = c.repo.total(TxType.EXPENSE, range.first, range.last)
            val b = BudgetMath.of(spent, settings.monthlyBudget, month)
            val pct = (b.fraction * 100).toInt()
            THRESHOLDS.firstOrNull { pct >= it }?.let { t ->
                val k = "overall|$key|$t"
                if (!prefs.getBoolean(k, false)) {
                    // Mark lower thresholds too, so crossing 100% straight away doesn't later fire 80%.
                    prefs.edit().apply { THRESHOLDS.filter { it <= t }.forEach { putBoolean("overall|$key|$it", true) } }.apply()
                    val text = if (t >= 100)
                        "You've spent ${Money.format(spent)} — ${Money.format(-b.left)} over your ${Money.format(settings.monthlyBudget)} budget."
                    else
                        "You've used $pct% of your ${Money.format(settings.monthlyBudget)} budget — ${Money.format(b.left)} left for ${b.daysLeft} days."
                    notify(ctx, 7_000, if (t >= 100) "Monthly budget exceeded" else "Budget $pct% used", text)
                }
            }
        }

        val totals = c.repo.observeCategoryTotals(TxType.EXPENSE, range.first, range.last).first()
        val budgeted = c.repo.categoryDao.byKind(CategoryKind.EXPENSE).filter { it.monthlyBudget > 0 }.associateBy { it.id }
        for (t in totals) {
            val cat = budgeted[t.categoryId] ?: continue
            val pct = (t.total * 100 / cat.monthlyBudget).toInt()
            val th = THRESHOLDS.firstOrNull { pct >= it } ?: continue
            val k = "cat${cat.id}|$key|$th"
            if (prefs.getBoolean(k, false)) continue
            prefs.edit().apply { THRESHOLDS.filter { it <= th }.forEach { putBoolean("cat${cat.id}|$key|$it", true) } }.apply()
            val text = if (th >= 100)
                "${cat.emoji} ${cat.name} is over budget by ${Money.format(t.total - cat.monthlyBudget)} (${Money.format(t.total)} of ${Money.format(cat.monthlyBudget)})."
            else
                "${cat.emoji} ${cat.name}: $pct% used — ${Money.format(cat.monthlyBudget - t.total)} left this month."
            notify(ctx, 7_100 + cat.id.toInt(), if (th >= 100) "${cat.name} over budget" else "${cat.name} budget $pct% used", text)
        }
    }

    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val pi = PendingIntent.getActivity(
            ctx, 7_000, MainActivity.intent(ctx, Routes.BUDGET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kosh)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When you reach 80% or 100% of a budget"
                }
            )
        }
    }
}
