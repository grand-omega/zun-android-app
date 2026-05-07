package dev.zun.flux.util

import retrofit2.HttpException
import java.io.IOException

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
        this is IOException -> "network unavailable"
        else -> message?.takeIf { it.isNotBlank() && it.length < 80 } ?: "unknown error"
    }
    return "Couldn't $action: $reason"
}
