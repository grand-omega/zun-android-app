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
    val isConnecting: Boolean = false,
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

            val oldLan = settings.lanUrl
            val oldTailscale = settings.tailscaleUrl
            val oldMode = settings.connectionMode
            val oldToken = settings.apiToken
            val oldServerUrl = settings.serverUrl
            val oldActiveRoute = settings.activeRoute

            updateDraft { copy(error = null, status = "Connecting...", isConnecting = true) }

            viewModelScope.launch {
                try {
                    settings.lanUrl = lan
                    settings.tailscaleUrl = tailscale
                    settings.connectionMode = draft.connectionMode
                    settings.apiToken = draft.token.trim()
                    app.networkResolver.refreshNow()
                    app.repository.listPrompts()
                    updateDraft { copy(status = "Connection settings active.", isConnecting = false) }
                } catch (t: Throwable) {
                    settings.lanUrl = oldLan
                    settings.tailscaleUrl = oldTailscale
                    settings.connectionMode = oldMode
                    settings.apiToken = oldToken
                    settings.serverUrl = oldServerUrl
                    settings.activeRoute = oldActiveRoute
                    app.rebuildRepository()
                    updateDraft {
                        copy(
                            error = if (t is retrofit2.HttpException && t.code() == 401) {
                                "Invalid API Token"
                            } else {
                                t.message ?: "Reconnect failed"
                            },
                            status = "Not saved",
                            isConnecting = false,
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            updateDraft {
                copy(
                    error = t.message ?: "Invalid connection settings",
                    status = "Not saved",
                    isConnecting = false,
                )
            }
        }
    }

    private fun updateDraft(block: ConnectionDraftState.() -> ConnectionDraftState) {
        _connectionDraft.value = _connectionDraft.value.block()
    }
}
