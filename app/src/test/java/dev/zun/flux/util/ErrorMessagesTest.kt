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
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class ErrorMessagesTest {

    @Test
    fun `IOException maps to network unavailable`() {
        val msg = IOException("connect timed out").toUserMessage("regenerate")
        assertEquals("Couldn't regenerate: network unavailable — check this device's own connection", msg)
    }

    @Test
    fun `connection refused maps to server not reachable`() {
        val msg = ConnectException("Connection refused").toUserMessage("connect")
        assertEquals("Couldn't connect: couldn't reach the server — is it running, and is the address/port correct?", msg)
    }

    @Test
    fun `no route to host maps to server not reachable`() {
        val msg = NoRouteToHostException("No route to host").toUserMessage("connect")
        assertEquals(
            "Couldn't connect: couldn't reach the server — check the address, and that this device can reach it on the network",
            msg,
        )
    }

    @Test
    fun `cleartext block maps to https requirement`() {
        val msg = IOException("CLEARTEXT communication to 192.168.1.10 not permitted").toUserMessage("connect")
        assertEquals("Couldn't connect: release builds require https:// server URLs", msg)
    }

    @Test
    fun `unknown host maps to dns failure`() {
        val msg = UnknownHostException("flux.invalid").toUserMessage("connect")
        assertEquals("Couldn't connect: server hostname cannot be resolved — check the address", msg)
    }

    @Test
    fun `timeout maps to server timed out`() {
        val msg = SocketTimeoutException("timeout").toUserMessage("connect")
        assertEquals("Couldn't connect: server timed out — it may be unreachable or overloaded", msg)
    }

    @Test
    fun `hostname certificate mismatch is explicit`() {
        val msg = SSLPeerUnverifiedException("Hostname 192.168.1.10 not verified").toUserMessage("connect")
        assertEquals("Couldn't connect: TLS certificate does not match this hostname", msg)
    }

    @Test
    fun `untrusted certificate is explicit`() {
        val msg = SSLHandshakeException("Trust anchor for certification path not found").toUserMessage("connect")
        assertEquals("Couldn't connect: TLS certificate is not trusted", msg)
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
