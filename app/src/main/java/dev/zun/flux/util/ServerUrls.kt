package dev.zun.flux.util

import java.net.URI

/**
 * Normalizes an optional server base URL for Retrofit/Coil.
 *
 * Blank values and untouched scheme placeholders are treated as absent. Present
 * values must be absolute http(s) URLs with a host and no query/fragment.
 */
fun normalizeOptionalServerUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed == "http://" || trimmed == "https://") return null

    val uri = runCatching { URI(trimmed) }.getOrNull()
        ?: throw IllegalArgumentException("URL is not valid")
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        throw IllegalArgumentException("URL must start with http:// or https://")
    }
    if (uri.host.isNullOrBlank()) {
        throw IllegalArgumentException("URL must include a host")
    }
    if (uri.rawQuery != null || uri.rawFragment != null) {
        throw IllegalArgumentException("URL must not include query or fragment text")
    }

    return trimmed.trimEnd('/')
}
