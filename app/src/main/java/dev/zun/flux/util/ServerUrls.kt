package dev.zun.flux.util

import java.net.URI

/**
 * Normalizes an optional server base URL for Retrofit/Coil.
 *
 * Blank values and untouched scheme placeholders are treated as absent. Present
 * values must be absolute http(s) URLs with a host and no query/fragment.
 */
fun normalizeOptionalServerUrl(raw: String): String? = normalizeOptionalServerUrl(raw = raw, defaultScheme = null, defaultPort = null, allowHttp = true)

fun normalizeOptionalLanServerUrl(
    raw: String,
    allowHttp: Boolean = true,
): String? = normalizeOptionalServerUrl(raw = raw, defaultScheme = "https", defaultPort = null, allowHttp = allowHttp)

fun normalizeOptionalTailscaleServerUrl(
    raw: String,
    allowHttp: Boolean = true,
): String? = normalizeOptionalServerUrl(raw = raw, defaultScheme = "https", defaultPort = null, allowHttp = allowHttp)

private fun normalizeOptionalServerUrl(
    raw: String,
    defaultScheme: String?,
    defaultPort: Int?,
    allowHttp: Boolean,
): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || trimmed == "http://" || trimmed == "https://") return null

    val hadScheme = trimmed.contains("://")
    val candidate = if (hadScheme || defaultScheme == null) trimmed else "$defaultScheme://$trimmed"
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
    if (uri.rawQuery != null || uri.rawFragment != null) {
        throw IllegalArgumentException("URL must not include query or fragment text")
    }

    val withDefaultPort = if (!hadScheme && uri.port == -1 && defaultPort != null) {
        URI(uri.scheme, uri.userInfo, uri.host, defaultPort, uri.rawPath, null, null).toString()
    } else {
        candidate
    }
    return withDefaultPort.trimEnd('/')
}
