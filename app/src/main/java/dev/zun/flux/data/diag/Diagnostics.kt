package dev.zun.flux.data.diag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val MAX_RECENT_ERRORS = 5

/** A single observed network failure, stored in the rolling buffer. */
data class DiagnosticsError(
    val timestampMs: Long,
    val path: String,
    val message: String,
)

/** Snapshot of recent network health, surfaced in the Settings → Diagnostics panel. */
data class DiagnosticsSnapshot(
    val lastSuccessAtMs: Long? = null,
    val recentErrors: List<DiagnosticsError> = emptyList(),
)

/**
 * In-memory ring of recent network successes/failures. The OkHttp interceptor
 * (see [okHttpInterceptor]) records every request so the Diagnostics panel can
 * show users when the server last responded vs. what failed last.
 *
 * Not persisted across process death — debugging is most useful "right now".
 */
class Diagnostics(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(DiagnosticsSnapshot())
    val state: StateFlow<DiagnosticsSnapshot> = _state.asStateFlow()

    fun recordSuccess() {
        _state.update { it.copy(lastSuccessAtMs = nowMs()) }
    }

    fun recordError(path: String, message: String) {
        val entry = DiagnosticsError(timestampMs = nowMs(), path = path, message = message)
        _state.update {
            it.copy(recentErrors = (listOf(entry) + it.recentErrors).take(MAX_RECENT_ERRORS))
        }
    }

    fun okHttpInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        try {
            val response: Response = chain.proceed(request)
            if (response.isSuccessful) {
                recordSuccess()
            } else {
                recordError(path, "HTTP ${response.code}")
            }
            response
        } catch (e: IOException) {
            recordError(path, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }
}
