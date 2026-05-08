package dev.zun.flux.data.net

import dev.zun.flux.util.DiscoveryHost
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ServerDiscoveryTest {

    @Test
    fun `valid health response is returned with version and comfy state`() = runTest {
        val discovery = ServerDiscovery(
            okHttpClientFactory = {
                stubClient { chain ->
                    val url = chain.request().url
                    if (url.scheme == "https" && url.host == "1.2.3.4" && url.port == 443) {
                        ok(chain, """{"status":"ok","version":"0.4.0","comfy":{"ok":true}}""")
                    } else {
                        notFound(chain)
                    }
                }
            },
        )

        val results = discovery.discover(DiscoveryHost("1.2.3.4", null))

        assertEquals(1, results.size)
        assertEquals("https://1.2.3.4:443", results[0].url)
        assertEquals("0.4.0", results[0].version)
        assertTrue(results[0].comfyOk)
    }

    @Test
    fun `response without version field is filtered out`() = runTest {
        val discovery = ServerDiscovery(
            okHttpClientFactory = {
                stubClient { chain ->
                    // 200 OK + valid JSON, but no `version` — looks like
                    // someone else's status page. Must not be returned.
                    ok(chain, """{"status":"ok"}""")
                }
            },
        )

        val results = discovery.discover(DiscoveryHost("1.2.3.4", null))

        assertEquals(emptyList<DiscoveredServer>(), results)
    }

    @Test
    fun `network errors on every probe yield empty list`() = runTest {
        val discovery = ServerDiscovery(
            okHttpClientFactory = {
                stubClient { throw IOException("boom") }
            },
        )

        val results = discovery.discover(DiscoveryHost("1.2.3.4", null))

        assertEquals(emptyList<DiscoveredServer>(), results)
    }

    @Test
    fun `explicit port is the only port probed`() = runTest {
        var probedPort: Int? = null
        val discovery = ServerDiscovery(
            okHttpClientFactory = {
                stubClient { chain ->
                    probedPort = portOf(chain.request().url.toString())
                    ok(chain, """{"status":"ok","version":"0.4.0"}""")
                }
            },
        )

        val results = discovery.discover(DiscoveryHost("1.2.3.4", port = 9090))

        assertEquals(9090, probedPort)
        // Both schemes still attempted on the user's port; whichever succeeds
        // first (or both — same client stub) should land in the results.
        assertTrue(results.all { it.url.endsWith(":9090") })
    }

    @Test
    fun `https and http both reachable returns both with https first`() = runTest {
        val discovery = ServerDiscovery(
            okHttpClientFactory = {
                stubClient { chain ->
                    val url = chain.request().url
                    when {
                        url.scheme == "https" && url.host == "1.2.3.4" && url.port == 443 ->
                            ok(chain, """{"status":"ok","version":"0.4.0"}""")

                        url.scheme == "http" && url.host == "1.2.3.4" && url.port == 8080 ->
                            ok(chain, """{"status":"ok","version":"0.4.0"}""")

                        else -> notFound(chain)
                    }
                }
            },
        )

        val results = discovery.discover(DiscoveryHost("1.2.3.4", null))

        assertEquals(2, results.size)
        assertEquals("https://1.2.3.4:443", results[0].url)
        assertEquals("http://1.2.3.4:8080", results[1].url)
    }

    @Test
    fun `non-2xx response is dropped`() = runTest {
        val discovery = ServerDiscovery(
            okHttpClientFactory = {
                stubClient { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body("".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            },
        )

        val results = discovery.discover(DiscoveryHost("1.2.3.4", null))

        assertEquals(emptyList<DiscoveredServer>(), results)
    }

    private fun stubClient(handle: (chain: Interceptor.Chain) -> Response): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain -> handle(chain) })
        .build()

    private fun ok(chain: Interceptor.Chain, json: String): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(json.toResponseBody("application/json".toMediaType()))
        .build()

    private fun notFound(chain: Interceptor.Chain): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(404)
        .message("Not Found")
        .body("".toResponseBody("application/json".toMediaType()))
        .build()

    private fun portOf(url: String): Int = url.substringAfter("://")
        .substringAfter(":")
        .substringBefore("/")
        .toInt()
}
