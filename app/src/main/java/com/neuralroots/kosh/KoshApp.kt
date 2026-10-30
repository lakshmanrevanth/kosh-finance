package com.neuralroots.kosh

import android.app.Application
import android.content.Context
import com.neuralroots.kosh.cloud.CloudScheduler
import com.neuralroots.kosh.cloud.CloudSync
import com.neuralroots.kosh.data.db.KoshDatabase
import com.neuralroots.kosh.data.prefs.SettingsStore
import com.neuralroots.kosh.data.repo.FinanceRepository
import com.neuralroots.kosh.reminder.ReminderScheduler
import com.neuralroots.kosh.sync.SyncScheduler
import com.neuralroots.kosh.util.Money
import com.neuralroots.kosh.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Manual DI container. Access with `context.container`. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: KoshDatabase = KoshDatabase.build(app)
    val settings = SettingsStore(app)
    val repo = FinanceRepository(db) {
        scope.launch {
            SyncScheduler.requestSync(app)
            CloudScheduler.requestSync(app)
            WidgetRefresher.refreshAll(app)
        }
    }
    /** Firebase cloud backup (users/{uid}/… in Firestore). */
    val cloud: CloudSync by lazy { CloudSync(app, db, repo, settings) }
}

class KoshApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val c = container
        c.scope.launch {
            c.repo.seedIfEmpty()
            c.settings.flow.map { it.currencyCode }.distinctUntilChanged().collect {
                Money.currencyCode = it
                WidgetRefresher.refreshAll(this@KoshApp)
            }
        }
        c.scope.launch {
            ReminderScheduler.schedule(this@KoshApp)
            SyncScheduler.schedulePeriodic(this@KoshApp)
            CloudScheduler.schedulePeriodic(this@KoshApp)
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as KoshApp).container
