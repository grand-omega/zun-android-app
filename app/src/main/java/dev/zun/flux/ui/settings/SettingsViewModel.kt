package dev.zun.flux.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.FluxApp
import dev.zun.flux.data.repo.ConnectionMode
import dev.zun.flux.util.normalizeOptionalServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectionDraftState(
    val lanUrl: String = "",
    val tailscaleUrl: String = "",
    val connectionMode: ConnectionMode = ConnectionMode.AUTO,
    val token: String = "",
    val error: String? = null,
    val status: String = "Current settings are active.",
)

class SettingsViewModel(
    private val app: FluxApp,
) : ViewModel() {
    private val settings = app.settingsManager

    private val _connectionDraft = MutableStateFlow(
        ConnectionDraftState(
            lanUrl = settings.lanUrl ?: "",
            tailscaleUrl = settings.tailscaleUrl ?: "",
            connectionMode = settings.connectionMode,
            token = settings.apiToken ?: "",
        ),
    )
    val connectionDraft: StateFlow<ConnectionDraftState> = _connectionDraft.asStateFlow()

    fun updateLanUrl(value: String) = updateDraft { copy(lanUrl = value, error = null, status = "Unsaved changes") }

    fun updateTailscaleUrl(value: String) = updateDraft { copy(tailscaleUrl = value, error = null, status = "Unsaved changes") }

    fun updateConnectionMode(value: ConnectionMode) = updateDraft { copy(connectionMode = value, error = null, status = "Unsaved changes") }

    fun updateToken(value: String) = updateDraft { copy(token = value, error = null, status = "Unsaved changes") }

    fun connect() {
        val draft = _connectionDraft.value
        try {
            val lan = normalizeOptionalServerUrl(draft.lanUrl)
            val tailscale = normalizeOptionalServerUrl(draft.tailscaleUrl)
            require(lan != null || tailscale != null) {
                "Enter at least one server URL"
            }
            require(draft.token.isNotBlank()) {
                "Enter an API token"
            }

            settings.lanUrl = lan
            settings.tailscaleUrl = tailscale
            settings.connectionMode = draft.connectionMode
            settings.apiToken = draft.token.trim()
            updateDraft { copy(error = null, status = "Connecting...") }

            viewModelScope.launch {
                try {
                    app.networkResolver.refreshNow()
                    updateDraft { copy(status = "Connection settings active.") }
                } catch (t: Throwable) {
                    updateDraft {
                        copy(
                            error = t.message ?: "Reconnect failed",
                            status = "Saved, reconnect failed",
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            updateDraft {
                copy(
                    error = t.message ?: "Invalid connection settings",
                    status = "Not saved",
                )
            }
        }
    }

    private fun updateDraft(block: ConnectionDraftState.() -> ConnectionDraftState) {
        _connectionDraft.value = _connectionDraft.value.block()
    }
}
