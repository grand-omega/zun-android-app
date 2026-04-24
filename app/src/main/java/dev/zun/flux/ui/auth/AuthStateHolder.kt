package dev.zun.flux.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zun.flux.data.repo.SettingsManager

class AuthStateHolder(private val settingsManager: SettingsManager) {

    var isAuthed by mutableStateOf(withinGraceWindow())
        private set

    fun markAuthed() {
        settingsManager.lastAuthTimestamp = System.currentTimeMillis()
        isAuthed = true
    }

    fun checkLock() {
        if (!withinGraceWindow()) {
            isAuthed = false
        }
    }

    private fun withinGraceWindow(): Boolean {
        val last = settingsManager.lastAuthTimestamp
        if (last == 0L) return false
        return System.currentTimeMillis() - last <= settingsManager.lockoutDurationMs
    }
}
