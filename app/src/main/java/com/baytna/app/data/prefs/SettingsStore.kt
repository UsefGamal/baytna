package com.baytna.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.baytna.domain.time.clampStartDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Which household member is using this phone. Set from the cloud membership once the phone is linked. */
    val currentMemberId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The household this phone syncs with the cloud, once sync is turned on or the phone joined one. */
    val linkedHouseholdId: String? = null,
    val lastSyncAt: Long? = null,
    /** Daily reminders for due recurring items and budgets close to their limit. */
    val remindersEnabled: Boolean = false,
    /** Day of the month a budget month starts (1 = calendar months). 25 makes "December" run 25 Nov to 24 Dec. */
    val monthStartDay: Int = 1,
)

private val Context.dataStore by preferencesDataStore(name = "baytna_settings")

class SettingsStore(private val context: Context) {
    private val memberKey = stringPreferencesKey("current_member_id")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val linkedKey = stringPreferencesKey("linked_household_id")
    private val lastSyncKey = longPreferencesKey("last_sync_at")
    private val remindersKey = booleanPreferencesKey("reminders_enabled")
    private val monthStartKey = intPreferencesKey("month_start_day")
    private val sentRemindersKey = stringSetPreferencesKey("sent_reminders")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            currentMemberId = p[memberKey],
            themeMode = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            linkedHouseholdId = p[linkedKey],
            lastSyncAt = p[lastSyncKey],
            remindersEnabled = p[remindersKey] ?: false,
            monthStartDay = clampStartDay(p[monthStartKey] ?: 1),
        )
    }

    suspend fun setCurrentMember(id: String) {
        context.dataStore.edit { it[memberKey] = id }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setLinkedHousehold(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(linkedKey)
            } else {
                prefs[linkedKey] = id
            }
        }
    }

    suspend fun setLastSyncAt(at: Long) {
        context.dataStore.edit { it[lastSyncKey] = at }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[remindersKey] = enabled }
    }

    suspend fun setMonthStartDay(day: Int) {
        context.dataStore.edit { it[monthStartKey] = clampStartDay(day) }
    }

    /** Reminder keys already shown, so each reminder appears once. */
    val sentReminders: Flow<Set<String>> = context.dataStore.data.map { it[sentRemindersKey] ?: emptySet() }

    suspend fun setSentReminders(keys: Set<String>) {
        context.dataStore.edit { it[sentRemindersKey] = keys }
    }
}
