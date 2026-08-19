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
        // Wall clock, so moving the device clock backwards past the last unlock makes `elapsed`
        // negative — which would satisfy a bare `<= lockoutDurationMs` and hand over an unlocked
        // app. Any backwards jump is treated as expired instead: re-authenticating costs one
        // biometric prompt, trusting it costs the lock entirely.
        val elapsed = System.currentTimeMillis() - last
        return elapsed >= 0 && elapsed <= settingsManager.lockoutDurationMs
    }
}
