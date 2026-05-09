package dev.zun.flux.data.net

import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.util.DiscoveryHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One reachable server uncovered by [ServerDiscovery]. The [url] is the
 * canonical base ready to hand to Retrofit (no trailing slash).
 */
data class DiscoveredServer(
    val url: String,
    val version: String,
    val comfyOk: Boolean,
)

/**
 * Probes a host across a small set of (scheme, port) candidates against
 * `/api/v1/health` to find any zun servers reachable on that host. The
 * health endpoint is unauthenticated, so this runs without a configured
 * token. A response is treated as a zun server only if it carries a
 * `version` field — that's what distinguishes us from a random JSON-200
 * web service.
 */
class ServerDiscovery(
    private val okHttpClientFactory: () -> OkHttpClient = { defaultClient() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(target: DiscoveryHost): List<DiscoveredServer> = coroutineScope {
        val candidates = if (target.port != null) {
            // User pinned a port — try both schemes on it, nothing else.
            SCHEMES.map { it to target.port }
        } else {
            DEFAULT_PORTS_BY_SCHEME.flatMap { (scheme, ports) ->
                ports.map { scheme to it }
            }
        }

        val client = okHttpClientFactory()
        candidates
            .map { (scheme, port) ->
                async(Dispatchers.IO) { probe(client, scheme, target.host, port) }
            }
            .awaitAll()
            .filterNotNull()
            .sortedWith(
                // HTTPS first, then ascending port. Stable enough for a UI list.
                compareBy(
                    { if (it.url.startsWith("https://")) 0 else 1 },
                    { it.url },
                ),
            )
    }

    private suspend fun probe(
        client: OkHttpClient,
        scheme: String,
        host: String,
        port: Int,
    ): DiscoveredServer? {
        val baseUrl = "$scheme://$host:$port"
        val request = Request.Builder()
            .url("$baseUrl/api/v1/health")
            .get()
            .build()

        // Cancellation must propagate to the underlying socket — withTimeoutOrNull
        // alone can't preempt blocking I/O. runInterruptible interrupts the IO
        // thread; we also cancel the OkHttp Call defensively in case the socket
        // call is at a non-interruptible point.
        val call = client.newCall(request)
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            runCatching {
                runInterruptible(Dispatchers.IO) {
                    call.execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body.string()
                        val parsed = runCatching {
                            json.decodeFromString(HealthResponse.serializer(), body)
                        }.getOrNull() ?: return@use null
                        val version = parsed.version ?: return@use null
                        DiscoveredServer(
                            url = baseUrl,
                            version = version,
                            comfyOk = parsed.comfy?.ok == true,
                        )
                    }
                }
            }.getOrNull()
        }.also { call.cancel() } // idempotent; ensures the socket is released on timeout
    }

    companion object {
        const val PROBE_TIMEOUT_MS = 1500L

        private val SCHEMES = listOf("https", "http")

        /** Tried for each scheme when the user didn't supply a port. */
        private val DEFAULT_PORTS_BY_SCHEME = mapOf(
            "http" to listOf(8080, 80, 8000),
            "https" to listOf(443, 8443),
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
}
