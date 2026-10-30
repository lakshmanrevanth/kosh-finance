package com.neuralroots.kosh.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.neuralroots.kosh.MainActivity
import com.neuralroots.kosh.container
import com.neuralroots.kosh.data.db.TxType
import com.neuralroots.kosh.data.db.Txn
import com.neuralroots.kosh.security.Biometric
import com.neuralroots.kosh.ui.theme.KoshTheme
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.launch

/**
 * Translucent "quick add" sheet launched from the home-screen widget (or anywhere else).
 * Extras: [EXTRA_TYPE], [EXTRA_CATEGORY], [EXTRA_ACCOUNT], [EXTRA_WIDGET].
 */
class QuickAddActivity : FragmentActivity() {

    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(EXTRA_WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID)
        val cfg = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetPrefs.load(this, widgetId) else null
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { TxType.valueOf(it) }.getOrNull() }
            ?: cfg?.defaultType ?: TxType.EXPENSE
        val categoryId = intent.getLongExtra(EXTRA_CATEGORY, -1).takeIf { it > 0 }
        val extraAccount = intent.getLongExtra(EXTRA_ACCOUNT, -1).takeIf { it > 0 }

        lifecycleScope.launch {
            val settings = container.settings.current()
            Money.currencyCode = settings.currencyCode
            val preferredAccount = extraAccount
                ?: cfg?.defaultAccountId?.takeIf { it > 0 }
                ?: settings.defaultAccountId.takeIf { it > 0 }
            setContent {
                KoshTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                    QuickAddSheet(
                        initialType = type,
                        initialCategoryId = categoryId,
                        preferredAccountId = preferredAccount,
                        onDismiss = { finish() },
                        onSave = { txn, label -> save(txn, label, cfg) },
                    )
                }
            }
        }
    }

    private fun save(txn: Txn, label: String, cfg: WidgetConfig?) {
        if (saving) return
        val doSave = {
            saving = true
            lifecycleScope.launch {
                try {
                    container.repo.saveTxn(txn)
                    val verb = when (txn.type) {
                        TxType.INCOME -> "received"
                        TxType.TRANSFER -> "transferred"
                        else -> "added"
                    }
                    Toast.makeText(
                        applicationContext,
                        "${Money.format(txn.amount)} $verb" + (if (label.isNotBlank()) " · $label" else ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                    WidgetRefresher.refreshAll(applicationContext)
                    if (cfg?.openAppAfterSave == true) startActivity(MainActivity.intent(this@QuickAddActivity))
                    finish()
                } catch (e: Exception) {
                    saving = false
                    Toast.makeText(applicationContext, e.message ?: "Could not save", Toast.LENGTH_LONG).show()
                }
            }
        }
        if (cfg?.requireAuth == true && Biometric.available(this)) {
            Biometric.prompt(this, "Confirm to save", "Kosh quick add", onSuccess = { doSave() })
        } else {
            doSave()
        }
    }

    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_CATEGORY = "categoryId"
        const val EXTRA_ACCOUNT = "accountId"
        const val EXTRA_WIDGET = "widgetId"

        fun intent(
            context: Context,
            type: TxType = TxType.EXPENSE,
            categoryId: Long? = null,
            accountId: Long? = null,
            widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        ): Intent = Intent(context, QuickAddActivity::class.java).apply {
            // Unique data so each widget button gets its own PendingIntent.
            data = Uri.parse("kosh://quickadd/$widgetId/${type.name}/${categoryId ?: -1}/${accountId ?: -1}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_TYPE, type.name)
            putExtra(EXTRA_WIDGET, widgetId)
            if (categoryId != null) putExtra(EXTRA_CATEGORY, categoryId)
            if (accountId != null) putExtra(EXTRA_ACCOUNT, accountId)
        }
    }
}
