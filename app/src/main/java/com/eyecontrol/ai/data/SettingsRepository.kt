package com.eyecontrol.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "eye_control_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val CAMERA_SELECTION = stringPreferencesKey("camera_selection")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
    }

    val darkThemeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME] ?: false
    }

    val cameraSelectionFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CAMERA_SELECTION] ?: "Front"
    }

    val voiceLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VOICE_LANGUAGE] ?: "en-US"
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_THEME] = enabled
        }
    }

    suspend fun setCameraSelection(camera: String) {
        context.dataStore.edit { preferences ->
            preferences[CAMERA_SELECTION] = camera
        }
    }

    suspend fun setVoiceLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[VOICE_LANGUAGE] = language
        }
    }

    suspend fun resetSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
