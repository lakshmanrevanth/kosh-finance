package com.neuralroots.kosh.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val currencyCode: String = "INR",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Google Apps Script Web App URL (…/exec). Empty = sync disabled. */
    val sheetUrl: String = "",
    /** Shared secret checked by the Apps Script. */
    val sheetToken: String = "",
    val autoSync: Boolean = true,
    val syncOnWifiOnly: Boolean = false,
    val lastSyncAt: Long = 0,
    val lastSyncError: String = "",
    val appLock: Boolean = false,
    val defaultAccountId: Long = -1,
    val remindersEnabled: Boolean = true,
    val reminderHour: Int = 9,
    val seeded: Boolean = false,
    /** Spreadsheet URL reported by the Apps Script (for "Open spreadsheet"). */
    val spreadsheetUrl: String = "",
    /** User chose "Continue without account" on the sign-in screen. */
    val cloudSkipped: Boolean = false,
    /** Firebase uid whose data this device currently holds ("" = none). */
    val cloudUid: String = "",
    /** Server time (ms) of the newest cloud change already pulled. */
    val lastCloudPull: Long = 0,
    /** Last successful upload to the cloud (ms). */
    val lastCloudPush: Long = 0,
    val cloudError: String = "",
) {
    val syncConfigured: Boolean get() = sheetUrl.startsWith("https://")
}

private val Context.dataStore by preferencesDataStore(name = "kosh_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val currency = stringPreferencesKey("currency")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val sheetUrl = stringPreferencesKey("sheet_url")
        val sheetToken = stringPreferencesKey("sheet_token")
        val autoSync = booleanPreferencesKey("auto_sync")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val appLock = booleanPreferencesKey("app_lock")
        val defaultAccount = longPreferencesKey("default_account")
        val reminders = booleanPreferencesKey("reminders")
        val reminderHour = intPreferencesKey("reminder_hour")
        val seeded = booleanPreferencesKey("seeded")
        val spreadsheetUrl = stringPreferencesKey("spreadsheet_url")
        val cloudSkipped = booleanPreferencesKey("cloud_skipped")
        val cloudUid = stringPreferencesKey("cloud_uid")
        val lastCloudPull = longPreferencesKey("last_cloud_pull")
        val lastCloudPush = longPreferencesKey("last_cloud_push")
        val cloudError = stringPreferencesKey("cloud_error")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        currencyCode = this[K.currency] ?: "INR",
        themeMode = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        dynamicColor = this[K.dynamic] ?: false,
        sheetUrl = this[K.sheetUrl] ?: "",
        sheetToken = this[K.sheetToken] ?: "",
        autoSync = this[K.autoSync] ?: true,
        syncOnWifiOnly = this[K.wifiOnly] ?: false,
        lastSyncAt = this[K.lastSyncAt] ?: 0,
        lastSyncError = this[K.lastSyncError] ?: "",
        appLock = this[K.appLock] ?: false,
        defaultAccountId = this[K.defaultAccount] ?: -1,
        remindersEnabled = this[K.reminders] ?: true,
        reminderHour = this[K.reminderHour] ?: 9,
        seeded = this[K.seeded] ?: false,
        spreadsheetUrl = this[K.spreadsheetUrl] ?: "",
        cloudSkipped = this[K.cloudSkipped] ?: false,
        cloudUid = this[K.cloudUid] ?: "",
        lastCloudPull = this[K.lastCloudPull] ?: 0,
        lastCloudPush = this[K.lastCloudPush] ?: 0,
        cloudError = this[K.cloudError] ?: "",
    )

    suspend fun setCurrency(code: String) = context.dataStore.edit { it[K.currency] = code }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[K.dynamic] = on }
    suspend fun setSheet(url: String, token: String) = context.dataStore.edit {
        it[K.sheetUrl] = url.trim(); it[K.sheetToken] = token.trim()
    }
    suspend fun setAutoSync(on: Boolean) = context.dataStore.edit { it[K.autoSync] = on }
    suspend fun setWifiOnly(on: Boolean) = context.dataStore.edit { it[K.wifiOnly] = on }
    suspend fun setSyncResult(at: Long, error: String) = context.dataStore.edit {
        if (error.isEmpty()) it[K.lastSyncAt] = at
        it[K.lastSyncError] = error
    }
    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[K.appLock] = on }
    suspend fun setDefaultAccount(id: Long) = context.dataStore.edit { it[K.defaultAccount] = id }
    suspend fun setReminders(on: Boolean) = context.dataStore.edit { it[K.reminders] = on }
    suspend fun setReminderHour(h: Int) = context.dataStore.edit { it[K.reminderHour] = h }
    suspend fun setSeeded() = context.dataStore.edit { it[K.seeded] = true }
    suspend fun setSpreadsheetUrl(url: String) = context.dataStore.edit { it[K.spreadsheetUrl] = url }
    suspend fun setCloudSkipped(on: Boolean) = context.dataStore.edit { it[K.cloudSkipped] = on }
    suspend fun setCloudUid(uid: String) = context.dataStore.edit { it[K.cloudUid] = uid }
    suspend fun setLastCloudPull(ms: Long) = context.dataStore.edit { it[K.lastCloudPull] = ms }
    suspend fun setCloudPushResult(at: Long, error: String) = context.dataStore.edit {
        if (error.isEmpty()) it[K.lastCloudPush] = at
        it[K.cloudError] = error
    }
    /** Forget cloud state (after sign-out). */
    suspend fun resetCloud() = context.dataStore.edit {
        it[K.cloudUid] = ""; it[K.lastCloudPull] = 0; it[K.lastCloudPush] = 0; it[K.cloudError] = ""
    }
}
