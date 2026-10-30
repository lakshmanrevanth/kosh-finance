package com.neuralroots.kosh.cloud

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.neuralroots.kosh.BuildConfig
import com.neuralroots.kosh.data.db.CloudOutboxItem
import com.neuralroots.kosh.data.db.KoshDatabase
import com.neuralroots.kosh.data.prefs.SettingsStore
import com.neuralroots.kosh.data.repo.FinanceRepository
import com.neuralroots.kosh.widget.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Two-way backup of the local Room database to Cloud Firestore under users/{uid}/…
 *
 *  - push: drains the cloud outbox (filled by FinanceRepository on every write).
 *  - pull: downloads documents changed since the last pull (server `syncedAt`) and merges
 *          them; newer `updatedAt` wins; deletions travel as `deleted: true` tombstones.
 *  - first sign-in on a device: full pull + merge, then uploads whatever the cloud lacks.
 */
class CloudSync(
    private val context: Context,
    private val db: KoshDatabase,
    private val repo: FinanceRepository,
    private val settings: SettingsStore,
) {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val fs: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val mutex = Mutex()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private fun userDoc(uid: String) = fs.collection("users").document(uid)

    /** True when the signed-in user's data is the data on this device. */
    suspend fun isReady(uid: String): Boolean = settings.current().cloudUid == uid

    /**
     * Called once after sign-in. If this device holds another account's data it is wiped first;
     * otherwise local data (e.g. from "continue without account") is merged into the account.
     */
    suspend fun onSignedIn(uid: String) = mutex.withLock {
        _busy.value = true
        try {
            val s = settings.current()
            if (s.cloudUid.isNotEmpty() && s.cloudUid != uid) wipeLocal()
            if (s.cloudUid != uid) settings.setLastCloudPull(0)

            auth.currentUser?.let { u ->
                userDoc(uid).set(
                    mapOf(
                        "email" to u.email, "name" to u.displayName,
                        "lastSeen" to FieldValue.serverTimestamp(), "appVersion" to BuildConfig.VERSION_NAME,
                    ),
                    SetOptions.merge(),
                ).await()
            }
            val cloudState = pullLocked(uid, full = true)
            // Upload every local row the cloud doesn't have (or where this device is newer).
            val toUpload = ArrayList<CloudOutboxItem>()
            for (col in CloudMapper.applyOrder) {
                val cloud = cloudState[col].orEmpty()
                for ((uuid, updated) in CloudMapper.localRows(db, col)) {
                    val cu = cloud[uuid]
                    if (cu == null || updated > cu) toUpload += CloudOutboxItem(collection = col, uuid = uuid)
                }
            }
            if (toUpload.isNotEmpty()) toUpload.chunked(500).forEach { db.cloudOutboxDao().putAll(it) }
            settings.setCloudUid(uid)
            settings.setCloudSkipped(false)
            pushLocked(uid)
            WidgetRefresher.refreshAll(context)
        } finally {
            _busy.value = false
        }
    }

    /** Pull remote changes then push local ones. Returns null on success, else an error message. */
    suspend fun syncNow(): String? = mutex.withLock {
        val uid = auth.currentUser?.uid ?: return@withLock "Not signed in"
        if (settings.current().cloudUid != uid) return@withLock "Account setup not finished"
        _busy.value = true
        try {
            val changed = pullLocked(uid, full = false).values.sumOf { it.size }
            pushLocked(uid)
            if (changed > 0) WidgetRefresher.refreshAll(context)
            null
        } catch (t: Throwable) {
            Log.w(TAG, "sync failed", t)
            val msg = friendly(t)
            settings.setCloudPushResult(0, msg)
            msg
        } finally {
            _busy.value = false
        }
    }

    // ---------------------------------------------------------------- push

    private suspend fun pushLocked(uid: String) {
        val outbox = db.cloudOutboxDao()
        while (true) {
            val items = outbox.pending(400)
            if (items.isEmpty()) break
            val batch = fs.batch()
            for (it in items) {
                val ref = userDoc(uid).collection(it.collection).document(it.uuid)
                val doc = if (it.deleted) null else CloudMapper.toDoc(db, it.collection, it.uuid)
                if (doc == null) {
                    batch.set(
                        ref,
                        mapOf("deleted" to true, "updatedAt" to System.currentTimeMillis(), "syncedAt" to FieldValue.serverTimestamp()),
                    )
                } else {
                    batch.set(ref, doc + mapOf("deleted" to false, "syncedAt" to FieldValue.serverTimestamp()))
                }
            }
            withTimeout(60_000) { batch.commit().await() }
            outbox.remove(items.map { it.id })
        }
        settings.setCloudPushResult(System.currentTimeMillis(), "")
    }

    // ---------------------------------------------------------------- pull

    /**
     * Downloads changed docs (all docs when [full]) and applies them in dependency order.
     * Returns, per collection, uuid -> cloud updatedAt of every doc seen.
     */
    private suspend fun pullLocked(uid: String, full: Boolean): Map<String, Map<String, Long>> {
        val since = if (full) 0L else settings.current().lastCloudPull
        var maxSynced = since
        val docs = LinkedHashMap<String, List<Pair<String, Map<String, Any?>>>>()
        for (col in CloudMapper.applyOrder) {
            var q = userDoc(uid).collection(col) as com.google.firebase.firestore.Query
            if (since > 0) q = q.whereGreaterThan("syncedAt", Timestamp(java.util.Date(since)))
            val snap = withTimeout(60_000) { q.get(Source.SERVER).await() }
            docs[col] = snap.documents.map { d ->
                d.getTimestamp("syncedAt")?.toDate()?.time?.let { if (it > maxSynced) maxSynced = it }
                d.id to (d.data ?: emptyMap())
            }
        }
        val seen = HashMap<String, Map<String, Long>>()
        db.withTransaction {
            for (col in CloudMapper.applyOrder) {
                val list = docs[col].orEmpty()
                val cloudUuids = list.map { it.first }.toSet()
                // When doing an incremental pull, also protect seeded rows already known to the cloud.
                for ((uuid, data) in list) CloudMapper.apply(db, col, uuid, data, cloudUuids)
                seen[col] = list.associate { it.first to CloudMapper.updatedAt(it.second) }
            }
            docs[CloudMapper.LOANS].orEmpty().forEach { (uuid, data) ->
                if (data["deleted"] != true) CloudMapper.relinkLoan(db, uuid, data)
            }
        }
        if (maxSynced > since) settings.setLastCloudPull(maxSynced)
        return seen
    }

    // ---------------------------------------------------------------- account lifecycle

    /** Clears every local table and re-seeds defaults. */
    private suspend fun wipeLocal() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        settings.resetCloud()
        repo.seedIfEmpty()
        WidgetRefresher.refreshAll(context)
    }

    /**
     * Signs out. Local data is removed from this phone (it stays safe in the cloud) unless
     * [keepLocal] is true. Pending uploads are attempted first.
     */
    suspend fun signOut(keepLocal: Boolean = false) {
        runCatching { syncNow() }
        mutex.withLock {
            auth.signOut()
            runCatching { AuthManager.clearCredentialState(context) }
            if (keepLocal) settings.resetCloud() else wipeLocal()
            settings.setCloudSkipped(false)
        }
    }

    /** Permanently deletes all cloud data and the Firebase account, then wipes this phone. */
    suspend fun deleteAccount(): String? = mutex.withLock {
        val user = auth.currentUser ?: return@withLock "Not signed in"
        try {
            val root = userDoc(user.uid)
            for (col in CloudMapper.applyOrder) {
                while (true) {
                    val snap = root.collection(col).limit(400).get(Source.SERVER).await()
                    if (snap.isEmpty) break
                    val b = fs.batch(); snap.documents.forEach { b.delete(it.reference) }; b.commit().await()
                }
            }
            root.delete().await()
            user.delete().await()
            runCatching { AuthManager.clearCredentialState(context) }
            wipeLocal()
            null
        } catch (t: Throwable) {
            Log.w(TAG, "delete failed", t)
            if (t is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException)
                "For safety, sign out and sign in again, then delete the account."
            else friendly(t)
        }
    }

    companion object {
        private const val TAG = "KoshCloud"

        fun friendly(t: Throwable): String = when {
            t is kotlinx.coroutines.TimeoutCancellationException -> "No internet connection — will retry automatically."
            t is com.google.firebase.firestore.FirebaseFirestoreException &&
                t.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permission denied by the server."
            t is com.google.firebase.firestore.FirebaseFirestoreException &&
                t.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Cloud is unreachable — will retry automatically."
            else -> t.message ?: t.javaClass.simpleName
        }
    }
}
