package dev.zun.flux.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.BuildConfig
import dev.zun.flux.FluxApp
import dev.zun.flux.data.repo.ConnectionMode
import dev.zun.flux.data.repo.OfflineCacheStats
import dev.zun.flux.util.normalizeOptionalLanServerUrl
import dev.zun.flux.util.normalizeOptionalTailscaleServerUrl
import dev.zun.flux.util.toUserMessage
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

data class OfflineCacheState(
    val stats: OfflineCacheStats = OfflineCacheStats(bytes = 0L, fileCount = 0),
    val isRefreshing: Boolean = false,
    val status: String = "Offline cache ready.",
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

    private val _offlineCache = MutableStateFlow(
        OfflineCacheState(stats = app.repository.offlineCacheStats()),
    )
    val offlineCache: StateFlow<OfflineCacheState> = _offlineCache.asStateFlow()

    fun updateLanUrl(value: String) = updateDraft { copy(lanUrl = value, error = null, status = "Unsaved changes") }

    fun updateTailscaleUrl(value: String) = updateDraft { copy(tailscaleUrl = value, error = null, status = "Unsaved changes") }

    fun updateConnectionMode(value: ConnectionMode) = updateDraft { copy(connectionMode = value, error = null, status = "Unsaved changes") }

    fun updateToken(value: String) = updateDraft { copy(token = value, error = null, status = "Unsaved changes") }

    fun connect() {
        val draft = _connectionDraft.value
        try {
            val lan = runCatching {
                normalizeOptionalLanServerUrl(draft.lanUrl, allowHttp = BuildConfig.DEBUG)
            }.getOrElse {
                throw IllegalArgumentException("Primary server: ${it.message}")
            }
            val tailscale = runCatching {
                normalizeOptionalTailscaleServerUrl(draft.tailscaleUrl, allowHttp = BuildConfig.DEBUG)
            }.getOrElse {
                throw IllegalArgumentException("Fallback server: ${it.message}")
            }
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
                    app.networkResolver.invalidateCache()
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
                            error = t.toUserMessage("connect"),
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

    fun refreshOfflineCache() {
        _offlineCache.value = _offlineCache.value.copy(isRefreshing = true, status = "Refreshing offline cache...")
        viewModelScope.launch {
            try {
                app.repository.syncHistory()
                _offlineCache.value = OfflineCacheState(
                    stats = app.repository.offlineCacheStats(),
                    status = "Offline cache refresh started. Images continue caching in the background.",
                )
            } catch (t: Throwable) {
                _offlineCache.value = _offlineCache.value.copy(
                    isRefreshing = false,
                    status = t.toUserMessage("connect"),
                )
            }
        }
    }

    fun clearOfflineCache() {
        app.repository.clearOfflineImageCache()
        _offlineCache.value = OfflineCacheState(
            stats = app.repository.offlineCacheStats(),
            status = "Offline image cache cleared.",
        )
    }

    private fun updateDraft(block: ConnectionDraftState.() -> ConnectionDraftState) {
        _connectionDraft.value = _connectionDraft.value.block()
    }
}
