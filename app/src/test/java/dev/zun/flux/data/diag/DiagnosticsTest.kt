package dev.zun.flux.data.diag

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DiagnosticsTest {

    @Test
    fun `success interceptor records last success`() {
        var nowMs = 1_000L
        val diag = Diagnostics(nowMs = { nowMs })

        diag.okHttpInterceptor().intercept(fakeChain(success = true, path = "/api/v1/health"))

        assertEquals(1_000L, diag.state.value.lastSuccessAtMs)
        assertTrue(diag.state.value.recentErrors.isEmpty())
    }

    @Test
    fun `non-2xx response is recorded as error`() {
        val diag = Diagnostics(nowMs = { 5_000L })

        diag.okHttpInterceptor().intercept(fakeChain(success = false, code = 500, path = "/api/v1/jobs"))

        assertEquals(1, diag.state.value.recentErrors.size)
        val err = diag.state.value.recentErrors.first()
        assertEquals("/api/v1/jobs", err.path)
        assertEquals("HTTP 500", err.message)
        assertNull(diag.state.value.lastSuccessAtMs)
    }

    @Test
    fun `IOException is recorded and rethrown`() {
        val diag = Diagnostics(nowMs = { 7_000L })
        val chain = throwingChain(IOException("connect timeout"), "/api/v1/health")

        try {
            diag.okHttpInterceptor().intercept(chain)
            error("expected IOException to propagate")
        } catch (_: IOException) { /* expected */ }

        assertEquals(1, diag.state.value.recentErrors.size)
        val err = diag.state.value.recentErrors.first()
        assertEquals("connect timeout", err.message)
    }

    @Test
    fun `recent errors buffer caps at five most recent`() {
        var t = 0L
        val diag = Diagnostics(nowMs = { t })

        repeat(8) { i ->
            t = (i + 1).toLong()
            diag.recordError("/path/$i", "err $i")
        }

        val errors = diag.state.value.recentErrors
        assertEquals(5, errors.size)
        // Newest first.
        assertEquals("err 7", errors.first().message)
        assertEquals("err 3", errors.last().message)
    }

    private fun fakeChain(success: Boolean, code: Int = 200, path: String): Interceptor.Chain = FakeChain { request ->
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (success) code else code)
            .message(if (success) "OK" else "Server Error")
            .body("".toResponseBody("text/plain".toMediaType()))
            .build()
    }.also { it.requestPath = path }

    private fun throwingChain(error: IOException, path: String): Interceptor.Chain = FakeChain { throw error }.also { it.requestPath = path }

    private class FakeChain(val onProceed: (Request) -> Response) : Interceptor.Chain {
        var requestPath: String = "/"
        override fun call() = error("unused")
        override fun connectTimeoutMillis() = 0
        override fun connection() = null
        override fun proceed(request: Request): Response = onProceed(request)
        override fun readTimeoutMillis() = 0
        override fun request(): Request = Request.Builder()
            .url("http://example.invalid$requestPath")
            .build()
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        override fun writeTimeoutMillis() = 0
    }
}
