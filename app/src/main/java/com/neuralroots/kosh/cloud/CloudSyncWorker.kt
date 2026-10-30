package com.neuralroots.kosh.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.neuralroots.kosh.container
import java.util.concurrent.TimeUnit

/** Background cloud backup: pull remote changes, push local ones. Retries with backoff when offline. */
class CloudSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val cloud = applicationContext.container.cloud
        if (!cloud.isReady(uid)) return Result.success()
        val err = cloud.syncNow()
        return when {
            err == null -> Result.success()
            runAttemptCount < 8 -> Result.retry()
            else -> Result.failure()
        }
    }
}

object CloudScheduler {
    private const val ONE_TIME = "kosh-cloud-sync"
    private const val PERIODIC = "kosh-cloud-sync-periodic"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Debounced sync after local changes (or on app open). No-op when signed out. */
    fun requestSync(context: Context, delaySeconds: Long = 3) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        val req = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.REPLACE, req)
    }

    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
