package com.eyecontrol.ai.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eyecontrol.ai.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val darkThemeFlow: Flow<Boolean> = repository.darkThemeFlow
    val cameraSelectionFlow: Flow<String> = repository.cameraSelectionFlow
    val voiceLanguageFlow: Flow<String> = repository.voiceLanguageFlow

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(enabled)
        }
    }

    fun setCameraSelection(camera: String) {
        viewModelScope.launch {
            repository.setCameraSelection(camera)
        }
    }

    fun setVoiceLanguage(language: String) {
        viewModelScope.launch {
            repository.setVoiceLanguage(language)
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            repository.resetSettings()
        }
    }
}
