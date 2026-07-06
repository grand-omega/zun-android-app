package dev.zun.flux.util

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Map a [Throwable] to a user-facing message in the form
 * `"Couldn't <action>: <reason>"`. Centralizes the choice of vocabulary so
 * raw `IOException` messages, HTTP status codes, and stack-trace text never
 * leak into the UI.
 *
 * Pass a verb-phrase action (e.g. `"regenerate"`, `"save the image"`,
 * `"connect"`). The result is title-case enough to drop into a Toast or
 * Snackbar without further formatting.
 */
fun Throwable.toUserMessage(action: String): String {
    val reason = when {
        this is HttpException && code() == 401 -> "API token rejected"
        this is HttpException && code() == 403 -> "API token rejected"
        this is HttpException && code() == 404 -> "server says it doesn't exist"
        this is HttpException && code() in 500..599 -> "server error (HTTP ${code()})"
        this is HttpException -> "HTTP ${code()}"
        this is SSLPeerUnverifiedException -> "TLS certificate does not match this hostname"
        this is SSLHandshakeException -> "TLS certificate is not trusted"
        this is UnknownHostException -> "server hostname cannot be resolved — check the address"
        this is ConnectException -> "couldn't reach the server — is it running, and is the address/port correct?"
        this is NoRouteToHostException -> "couldn't reach the server — check the address, and that this device can reach it on the network"
        this is SocketTimeoutException -> "server timed out — it may be unreachable or overloaded"
        this is IOException && message?.contains("CLEARTEXT", ignoreCase = true) == true -> "release builds require https:// server URLs"
        this is IOException && message?.contains("Unable to resolve host", ignoreCase = true) == true -> "server hostname cannot be resolved — check the address"
        this is IOException -> "network unavailable — check this device's own connection"
        else -> message?.takeIf { it.isNotBlank() && it.length < 80 } ?: "unknown error"
    }
    return "Couldn't $action: $reason"
}
