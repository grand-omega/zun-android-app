package dev.zun.flux.data.repo

import dev.zun.flux.data.repo.ConnectionDiagnosis.HostUnreachable
import dev.zun.flux.data.repo.ConnectionDiagnosis.InvalidUrl
import dev.zun.flux.data.repo.ConnectionDiagnosis.NoServerUrl
import dev.zun.flux.data.repo.ConnectionDiagnosis.Reachable
import dev.zun.flux.data.repo.ConnectionDiagnosis.ServiceNotListening
import dev.zun.flux.data.repo.ConnectionDiagnosis.Unknown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

class ConnectionDiagnoser(
    private val settingsManager: SettingsManager,
) {
    suspend fun diagnose(): ConnectionDiagnosis = withContext(Dispatchers.IO) {
        val baseUrl = settingsManager.serverUrl?.takeUnless { it.isBlank() } ?: return@withContext NoServerUrl
        val uri = runCatching { URI(baseUrl) }.getOrElse { return@withContext InvalidUrl(it.message ?: "Invalid server URL") }
        val host = uri.host ?: return@withContext InvalidUrl("Server URL is missing a host")
        val port = if (uri.port == -1) {
            if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
        } else {
            uri.port
        }

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), DIAGNOSE_TIMEOUT_MS)
            }
            Reachable
        } catch (e: ConnectException) {
            ServiceNotListening("Host is reachable, but nothing is listening on port $port")
        } catch (e: SocketTimeoutException) {
            HostUnreachable("Timed out reaching $host:$port. The PC may be off, asleep, or unreachable over this network.")
        } catch (e: NoRouteToHostException) {
            HostUnreachable("No route to $host:$port. Check Wi-Fi, VPN, or Tailscale connectivity.")
        } catch (e: UnknownHostException) {
            HostUnreachable("Cannot resolve $host. Check DNS, MagicDNS, or the server URL.")
        } catch (e: IOException) {
            Unknown(e.message ?: "Network error while checking server")
        }
    }

    private companion object {
        const val DIAGNOSE_TIMEOUT_MS = 1_200
    }
}
