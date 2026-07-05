package dev.zun.flux.util

import java.net.URI

/**
 * Normalizes an optional server base URL for Retrofit/Coil.
 *
 * Blank values and untouched scheme placeholders are treated as absent. Bare
 * hosts default to https. Present values must be http(s) URLs with a host and
 * no query/fragment.
 */
fun normalizeOptionalServerUrl(
    raw: String,
    allowHttp: Boolean = true,
    blockHost: String? = null,
): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed == "http://" || trimmed == "https://") return null

    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    val uri = runCatching { URI(candidate) }.getOrNull()
        ?: throw IllegalArgumentException("URL is not valid")
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        throw IllegalArgumentException("URL must start with http:// or https://")
    }
    if (scheme == "http" && !allowHttp) {
        throw IllegalArgumentException("Release builds require https:// server URLs")
    }
    if (uri.host.isNullOrBlank()) {
        throw IllegalArgumentException("URL must include a host")
    }
    if (blockHost != null && uri.host.equals(blockHost, ignoreCase = true)) {
        throw IllegalArgumentException("This is the production server — use your local dev server instead.")
    }
    if (uri.rawQuery != null || uri.rawFragment != null) {
        throw IllegalArgumentException("URL must not include query or fragment text")
    }
    return candidate.trimEnd('/')
}
