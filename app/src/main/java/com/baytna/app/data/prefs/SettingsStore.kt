package com.baytna.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
)

private val Context.dataStore by preferencesDataStore(name = "baytna_settings")

class SettingsStore(private val context: Context) {
    private val memberKey = stringPreferencesKey("current_member_id")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val linkedKey = stringPreferencesKey("linked_household_id")
    private val lastSyncKey = longPreferencesKey("last_sync_at")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            currentMemberId = p[memberKey],
            themeMode = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            linkedHouseholdId = p[linkedKey],
            lastSyncAt = p[lastSyncKey],
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
}
