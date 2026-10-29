package com.calmvolume.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun open(job: Job) =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { tmp.root.resolve("s.preferences_pb") }

    @Test
    fun defaultsToOffMedium() = runBlocking {
        val store = SettingsStore(open(SupervisorJob()))
        assertEquals(Settings(enabled = false, preset = Preset.Medium), store.settings.first())
    }

    @Test
    fun valuesSurviveReopeningTheFile() = runBlocking {
        val firstJob = SupervisorJob()
        SettingsStore(open(firstJob)).run {
            setEnabled(true)
            setPreset(Preset.Strong)
        }
        firstJob.cancel() // closes the first DataStore, like a process restart

        val reopened = SettingsStore(open(SupervisorJob()))
        assertEquals(Settings(enabled = true, preset = Preset.Strong), reopened.settings.first())
    }

    @Test
    fun unknownPresetNameFallsBackToMedium() = runBlocking {
        val ds = open(SupervisorJob())
        ds.edit { it[stringPreferencesKey("preset")] = "Bogus" }
        assertEquals(Preset.Medium, SettingsStore(ds).settings.first().preset)
    }
}
