package com.calmvolume.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(val enabled: Boolean = false, val preset: Preset = Preset.Medium)

class SettingsStore(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data.map {
        Settings(
            enabled = it[ENABLED] ?: false,
            // An unknown name (e.g. a preset removed in a later version) falls back to the default
            preset = Preset.entries.firstOrNull { p -> p.name == it[PRESET] } ?: Preset.Medium,
        )
    }

    suspend fun setEnabled(enabled: Boolean) = store.edit { it[ENABLED] = enabled }
    suspend fun setPreset(preset: Preset) = store.edit { it[PRESET] = preset.name }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val PRESET = stringPreferencesKey("preset")
    }
}

private val Context.dataStore by preferencesDataStore("settings")

fun Context.settingsStore() = SettingsStore(applicationContext.dataStore)
