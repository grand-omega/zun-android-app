package dev.zun.flux.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AuthStateHolder {
    private var lastAuthTime = 0L
    private val gracePeriodMs = 60_000L

    var isAuthed by mutableStateOf(false)
        private set

    fun markAuthed() {
        isAuthed = true
        lastAuthTime = System.currentTimeMillis()
    }

    fun checkLock() {
        if (System.currentTimeMillis() - lastAuthTime > gracePeriodMs) {
            isAuthed = false
        }
    }
}
