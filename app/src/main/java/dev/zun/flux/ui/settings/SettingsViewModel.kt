package dev.zun.flux.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.BuildConfig
import dev.zun.flux.FluxApp
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.repo.OfflineCacheStats
import dev.zun.flux.util.normalizeOptionalServerUrl
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConnectionDraftState(
    val serverUrl: String = "",
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
            serverUrl = settings.serverUrl ?: "",
            token = settings.apiToken ?: "",
        ),
    )
    val connectionDraft: StateFlow<ConnectionDraftState> = _connectionDraft.asStateFlow()

    private val _offlineCache = MutableStateFlow(
        OfflineCacheState(status = "Calculating cache size..."),
    )
    val offlineCache: StateFlow<OfflineCacheState> = _offlineCache.asStateFlow()

    /** Best-effort snapshot of /health for the Diagnostics panel; null until fetched or on failure. */
    private val _serverHealth = MutableStateFlow<HealthResponse?>(null)
    val serverHealth: StateFlow<HealthResponse?> = _serverHealth.asStateFlow()

    init {
        viewModelScope.launch {
            // stats() walks the whole cache dir — keep it off the main thread.
            val stats = withContext(Dispatchers.IO) { app.repositories.images.offlineCacheStats() }
            _offlineCache.value = _offlineCache.value.copy(stats = stats, status = "Offline cache ready.")
        }
        viewModelScope.launch {
            _serverHealth.value = runCatching { app.repositories.health.health() }
                .onFailure { Log.w(TAG, "Diagnostics health fetch failed", it) }
                .getOrNull()
        }
    }

    fun updateServerUrl(value: String) = updateDraft { copy(serverUrl = value, error = null, status = "Unsaved changes") }

    fun updateToken(value: String) = updateDraft { copy(token = value, error = null, status = "Unsaved changes") }

    fun connect() {
        val draft = _connectionDraft.value
        try {
            val url = requireNotNull(
                normalizeOptionalServerUrl(
                    draft.serverUrl,
                    allowHttp = BuildConfig.DEBUG,
                    blockHost = if (BuildConfig.DEBUG) "zun.h.doremysweet.com" else null,
                ),
            ) {
                "Enter a server URL"
            }
            require(draft.token.isNotBlank()) {
                "Enter an API token"
            }

            val oldServerUrl = settings.serverUrl
            val oldToken = settings.apiToken

            updateDraft { copy(error = null, status = "Connecting...", isConnecting = true) }

            viewModelScope.launch {
                try {
                    settings.serverUrl = url
                    settings.apiToken = draft.token.trim()
                    app.rebuildRepository()
                    app.repositories.prompts.listPrompts()
                    updateDraft { copy(status = "Connection settings active.", isConnecting = false) }
                } catch (t: Throwable) {
                    settings.serverUrl = oldServerUrl
                    settings.apiToken = oldToken
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
                app.repositories.jobs.syncHistory()
                _offlineCache.value = OfflineCacheState(
                    stats = withContext(Dispatchers.IO) { app.repositories.images.offlineCacheStats() },
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
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) {
                app.repositories.images.clearOfflineImageCache()
                app.repositories.images.offlineCacheStats()
            }
            _offlineCache.value = OfflineCacheState(
                stats = stats,
                status = "Offline image cache cleared.",
            )
        }
    }

    private fun updateDraft(block: ConnectionDraftState.() -> ConnectionDraftState) {
        _connectionDraft.value = _connectionDraft.value.block()
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
