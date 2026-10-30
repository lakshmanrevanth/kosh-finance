package com.neuralroots.kosh.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neuralroots.kosh.container
import kotlinx.coroutines.launch

/** Handles the "Mark paid" action on an EMI reminder notification. */
class EmiActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_PAID) return
        val emiId = intent.getLongExtra(EXTRA_EMI_ID, -1)
        val n = intent.getIntExtra(EXTRA_INSTALLMENT, -1)
        if (emiId < 0 || n < 1) return
        val app = context.applicationContext
        val c = app.container
        val pending = goAsync()
        c.scope.launch {
            try {
                val emi = c.repo.emiDao.get(emiId) ?: return@launch
                val active = c.repo.accountDao.active()
                val defaultId = c.settings.current().defaultAccountId
                val accountId = emi.accountId?.takeIf { id -> active.any { it.id == id } }
                    ?: defaultId.takeIf { id -> active.any { it.id == id } }
                    ?: active.firstOrNull()?.id
                c.repo.markEmiPaid(emiId, n, emi.emiAmount, System.currentTimeMillis(), accountId)
                Notifications.emiMarkedPaid(app, emi, n)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_PAID = "com.neuralroots.kosh.action.MARK_EMI_PAID"
        const val EXTRA_EMI_ID = "emiId"
        const val EXTRA_INSTALLMENT = "installment"
    }
}
