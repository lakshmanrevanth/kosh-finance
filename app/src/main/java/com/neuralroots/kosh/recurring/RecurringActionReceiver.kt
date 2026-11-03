package com.neuralroots.kosh.recurring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neuralroots.kosh.container
import com.neuralroots.kosh.util.Money
import kotlinx.coroutines.launch

/** Handles the "Paid" / "Skip" buttons on bill notifications. */
class RecurringActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id <= 0) return
        val pending = goAsync()
        val c = context.container
        c.scope.launch {
            try {
                val r = c.repo.recurringDao.get(id) ?: return@launch
                when (intent.action) {
                    ACTION_PAID -> {
                        c.repo.postRecurring(id)
                        RecurringNotifications.recorded(context, id, "${r.name} ${Money.format(r.amount)} recorded ✓")
                    }
                    ACTION_SKIP -> {
                        c.repo.skipRecurring(id)
                        RecurringNotifications.recorded(context, id, "${r.name} skipped this time")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAID = "com.neuralroots.kosh.recurring.PAID"
        const val ACTION_SKIP = "com.neuralroots.kosh.recurring.SKIP"
        const val EXTRA_ID = "recurringId"
    }
}
