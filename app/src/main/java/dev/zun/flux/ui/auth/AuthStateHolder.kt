package dev.zun.flux.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zun.flux.data.repo.SettingsManager

class AuthStateHolder(private val settingsManager: SettingsManager) {
    private var lastAuthTime = 0L

    var isAuthed by mutableStateOf(false)
        private set

    fun markAuthed() {
        isAuthed = true
        lastAuthTime = System.currentTimeMillis()
    }

    fun checkLock() {
        if (System.currentTimeMillis() - lastAuthTime > settingsManager.lockoutDurationMs) {
            isAuthed = false
        }
    }
}
