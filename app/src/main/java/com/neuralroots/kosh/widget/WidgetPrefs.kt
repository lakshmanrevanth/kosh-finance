package com.neuralroots.kosh.widget

import android.content.Context
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.prefs.ThemeMode

enum class BalanceMode(val label: String) { NONE("Hide"), TOTAL("Net worth"), ACCOUNT("One account") }

/** Per-widget settings, stored in SharedPreferences keyed by appWidgetId. */
data class WidgetConfig(
    val defaultAccountId: Long = -1,
    val defaultType: TxType = TxType.EXPENSE,
    /** Empty = the first 4 expense categories. */
    val quickCategoryIds: List<Long> = emptyList(),
    val balanceMode: BalanceMode = BalanceMode.TOTAL,
    val balanceAccountId: Long = -1,
    val showToday: Boolean = true,
    /** 100, 85 or 70. */
    val opacity: Int = 100,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val requireAuth: Boolean = false,
    val openAppAfterSave: Boolean = false,
)

object WidgetPrefs {
    private const val FILE = "kosh_widgets"

    private fun k(id: Int, key: String) = "w${id}_$key"

    fun load(context: Context, id: Int): WidgetConfig {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = WidgetConfig()
        return WidgetConfig(
            defaultAccountId = p.getLong(k(id, "account"), d.defaultAccountId),
            defaultType = p.getString(k(id, "type"), null)?.let { runCatching { TxType.valueOf(it) }.getOrNull() } ?: d.defaultType,
            quickCategoryIds = p.getString(k(id, "cats"), "")!!.split(',').mapNotNull { it.toLongOrNull() },
            balanceMode = p.getString(k(id, "balance"), null)?.let { runCatching { BalanceMode.valueOf(it) }.getOrNull() } ?: d.balanceMode,
            balanceAccountId = p.getLong(k(id, "balanceAccount"), d.balanceAccountId),
            showToday = p.getBoolean(k(id, "today"), d.showToday),
            opacity = p.getInt(k(id, "opacity"), d.opacity),
            theme = p.getString(k(id, "theme"), null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            requireAuth = p.getBoolean(k(id, "auth"), d.requireAuth),
            openAppAfterSave = p.getBoolean(k(id, "openApp"), d.openAppAfterSave),
        )
    }

    fun save(context: Context, id: Int, c: WidgetConfig) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(k(id, "account"), c.defaultAccountId)
            .putString(k(id, "type"), c.defaultType.name)
            .putString(k(id, "cats"), c.quickCategoryIds.joinToString(","))
            .putString(k(id, "balance"), c.balanceMode.name)
            .putLong(k(id, "balanceAccount"), c.balanceAccountId)
            .putBoolean(k(id, "today"), c.showToday)
            .putInt(k(id, "opacity"), c.opacity)
            .putString(k(id, "theme"), c.theme.name)
            .putBoolean(k(id, "auth"), c.requireAuth)
            .putBoolean(k(id, "openApp"), c.openAppAfterSave)
            .apply()
    }

    fun delete(context: Context, id: Int) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("w${id}_") }.forEach { e.remove(it) }
        e.apply()
    }
}
