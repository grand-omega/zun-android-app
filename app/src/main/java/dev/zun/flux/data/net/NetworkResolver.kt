package dev.zun.flux.data.net

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
 */
class NetworkResolver(
    private val settings: SettingsManager,
    private val onActiveUrlChanged: () -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    fun refresh(): Job = scope.launch { refreshNow() }

    suspend fun refreshNow() = mutex.withLock {
        val lan = settings.lanUrl?.takeUnless { it.isBlank() }
        val ts = settings.tailscaleUrl?.takeUnless { it.isBlank() }

        val chosen = when (settings.connectionMode) {
            ConnectionMode.AUTO -> when {
                lan != null && probe(lan) -> lan to ActiveRoute.LAN
                ts != null -> ts to ActiveRoute.TAILSCALE
                lan != null -> lan to ActiveRoute.LAN
                else -> null
            }
            ConnectionMode.LAN_ONLY -> lan?.let { it to ActiveRoute.LAN }
            ConnectionMode.TAILSCALE_ONLY -> ts?.let { it to ActiveRoute.TAILSCALE }
        } ?: return@withLock

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
                s.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_MS = 400
    }
}
