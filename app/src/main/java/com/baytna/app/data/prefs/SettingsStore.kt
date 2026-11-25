package com.baytna.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Which household member is using this phone (until cloud sign-in exists). */
    val currentMemberId: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

private val Context.dataStore by preferencesDataStore(name = "baytna_settings")

class SettingsStore(private val context: Context) {
    private val memberKey = stringPreferencesKey("current_member_id")
    private val themeKey = stringPreferencesKey("theme_mode")

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            currentMemberId = p[memberKey],
            themeMode = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun setCurrentMember(id: String) {
        context.dataStore.edit { it[memberKey] = id }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }
}
