package dev.zun.flux.data.net

import dev.zun.flux.Tuning
import dev.zun.flux.data.repo.ActiveRoute
import dev.zun.flux.data.repo.ConnectionMode
import dev.zun.flux.data.repo.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Picks between LAN and Tailscale base URLs by TCP-probing the LAN address with a
 * short timeout. Result is written to [SettingsManager.serverUrl] and the caller
 * is asked to rebuild the Retrofit/OkHttp stack via [onActiveUrlChanged].
 *
 * Re-runs on every network change so cellular ↔ Wi-Fi switching is automatic.
 *
 * Probe results are cached briefly (see [Tuning.NETWORK_RESOLVE_CACHE_MS]) so a
 * burst of ConnectivityManager callbacks (common when WiFi flaps) doesn't storm
 * the server.
 */
class NetworkResolver(
    private val settings: SettingsManager,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val onActiveUrlChanged: () -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    @Volatile
    private var lastResolvedAtMs: Long = 0L

    fun refresh(): Job = scope.launch { refreshNow() }

    /** Forces a re-probe even if the cached result is still fresh. */
    fun invalidateCache() {
        lastResolvedAtMs = 0L
    }

    suspend fun refreshNow() = mutex.withLock {
        if (nowMs() - lastResolvedAtMs < Tuning.NETWORK_RESOLVE_CACHE_MS) {
            return@withLock
        }
        val lan = settings.lanUrl?.takeUnless { it.isBlank() }
        val ts = settings.tailscaleUrl?.takeUnless { it.isBlank() }

        val chosen = chooseActiveRoute(
            mode = settings.connectionMode,
            lanUrl = lan,
            tailscaleUrl = ts,
            isLanReachable = lan?.let { probe(it) } ?: false,
        )

        lastResolvedAtMs = nowMs()

        if (chosen == null) {
            if (settings.serverUrl != null || settings.activeRoute != ActiveRoute.NONE) {
                withContext(Dispatchers.Main) {
                    settings.serverUrl = null
                    settings.activeRoute = ActiveRoute.NONE
                    onActiveUrlChanged()
                }
            }
            return@withLock
        }

        if (chosen.first != settings.serverUrl || chosen.second != settings.activeRoute) {
            withContext(Dispatchers.Main) {
                settings.serverUrl = chosen.first
                settings.activeRoute = chosen.second
                onActiveUrlChanged()
            }
        }
    }

    private fun probe(base: String): Boolean {
        val uri = runCatching { URI(base) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        val port = if (uri.port == -1) {
            if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
        } else {
            uri.port
        }
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), Tuning.NETWORK_PROBE_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

internal fun chooseActiveRoute(
    mode: ConnectionMode,
    lanUrl: String?,
    tailscaleUrl: String?,
    isLanReachable: Boolean,
): Pair<String, ActiveRoute>? = when (mode) {
    ConnectionMode.AUTO -> when {
        lanUrl != null && isLanReachable -> lanUrl to ActiveRoute.LAN
        tailscaleUrl != null -> tailscaleUrl to ActiveRoute.TAILSCALE
        lanUrl != null -> lanUrl to ActiveRoute.LAN
        else -> null
    }

    ConnectionMode.LAN_ONLY -> lanUrl?.let { it to ActiveRoute.LAN }

    ConnectionMode.TAILSCALE_ONLY -> tailscaleUrl?.let { it to ActiveRoute.TAILSCALE }
}
