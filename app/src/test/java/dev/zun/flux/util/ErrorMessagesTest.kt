package dev.zun.flux.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ErrorMessagesTest {

    @Test
    fun `IOException maps to network unavailable`() {
        val msg = IOException("connect timed out").toUserMessage("regenerate")
        assertEquals("Couldn't regenerate: network unavailable", msg)
    }

    @Test
    fun `401 maps to token rejected`() {
        val msg = httpError(401).toUserMessage("connect")
        assertEquals("Couldn't connect: API token rejected", msg)
    }

    @Test
    fun `403 also maps to token rejected`() {
        val msg = httpError(403).toUserMessage("delete")
        assertEquals("Couldn't delete: API token rejected", msg)
    }

    @Test
    fun `404 surfaces missing resource`() {
        val msg = httpError(404).toUserMessage("load")
        assertEquals("Couldn't load: server says it doesn't exist", msg)
    }

    @Test
    fun `5xx mentions server error`() {
        val msg = httpError(503).toUserMessage("save")
        assertEquals("Couldn't save: server error (HTTP 503)", msg)
    }

    @Test
    fun `unhandled HTTP code falls through to bare HTTP X`() {
        val msg = httpError(418).toUserMessage("brew")
        assertEquals("Couldn't brew: HTTP 418", msg)
    }

    @Test
    fun `generic exception with short message uses it`() {
        val msg = IllegalStateException("boom").toUserMessage("act")
        assertEquals("Couldn't act: boom", msg)
    }

    @Test
    fun `generic exception with long message falls back to unknown error`() {
        val long = "x".repeat(200)
        val msg = IllegalStateException(long).toUserMessage("act")
        assertEquals("Couldn't act: unknown error", msg)
    }

    private fun httpError(code: Int): HttpException = HttpException(
        Response.error<Unit>(
            "".toResponseBody("application/json".toMediaType()),
            okhttp3.Response.Builder()
                .request(Request.Builder().url("http://x/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("err")
                .build(),
        ),
    )
}
