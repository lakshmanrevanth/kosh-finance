package com.neuralroots.kosh.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.neuralroots.kosh.container
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val ONE_TIME = "kosh-sync"
    private const val PERIODIC = "kosh-sync-periodic"

    /**
     * Queues a sync. Without [force] it only runs when auto-sync is on.
     * Settings are read asynchronously so this is safe to call from anywhere.
     */
    fun requestSync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val c = app.container
        c.scope.launch {
            val s = c.settings.current()
            if (!s.syncConfigured || (!force && !s.autoSync)) return@launch
            val network = if (s.syncOnWifiOnly && !force) NetworkType.UNMETERED else NetworkType.CONNECTED
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(SyncWorker.KEY_FORCE to force))
                .build()
            WorkManager.getInstance(app).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }

    /** Safety net: retries anything left in the outbox every 6 hours. */
    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** True while a sync is queued or running. */
    fun observeRunning(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context.applicationContext).getWorkInfosForUniqueWorkFlow(ONE_TIME)
            .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
}
