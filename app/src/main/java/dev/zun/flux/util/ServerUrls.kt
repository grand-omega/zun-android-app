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

/** A host (and optional port) extracted from free-form discovery input. */
data class DiscoveryHost(val host: String, val port: Int?)

/**
 * Strips any scheme, path, query, or fragment the user may have pasted, then
 * splits an optional `:port`. Returns null if the input doesn't look like a
 * host. Lets the discovery flow accept all of:
 *   `192.168.1.5`, `192.168.1.5:8080`, `https://flux.local`, `flux.local/`
 */
fun parseDiscoveryHost(raw: String): DiscoveryHost? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isBlank()) return null

    // If it has a scheme, parse via URI to extract host + port cleanly.
    val withoutScheme = if (trimmed.contains("://")) {
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        return DiscoveryHost(host, uri.port.takeIf { it != -1 })
    } else {
        trimmed
    }

    // No scheme: split on the *last* colon for host:port. Handle IPv6 in
    // brackets (`[::1]:8080`) — just strip brackets, no port unless present.
    if (withoutScheme.startsWith("[")) {
        val close = withoutScheme.indexOf(']')
        if (close == -1) return null
        val host = withoutScheme.substring(1, close)
        if (host.isBlank()) return null
        val tail = withoutScheme.substring(close + 1)
        val port = if (tail.startsWith(":")) {
            tail.substring(1).toIntOrNull() ?: return null
        } else if (tail.isBlank()) {
            null
        } else {
            return null
        }
        return DiscoveryHost(host, port)
    }

    val colonIdx = withoutScheme.lastIndexOf(':')
    return if (colonIdx == -1) {
        DiscoveryHost(withoutScheme, null)
    } else {
        val host = withoutScheme.substring(0, colonIdx)
        val port = withoutScheme.substring(colonIdx + 1).toIntOrNull() ?: return null
        if (host.isBlank() || port !in 1..65535) return null
        DiscoveryHost(host, port)
    }
}

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
