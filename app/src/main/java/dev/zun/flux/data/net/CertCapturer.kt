package dev.zun.flux.data.net

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.security.cert.X509Certificate

/**
 * One-shot probe: open a connection to [url], read the leaf certificate, and
 * return OkHttp's pin token (`sha256/...`). Used by the "Pin current
 * certificates" affordance in Settings.
 *
 * Returns null for non-HTTPS urls (nothing to pin) or on connection failure.
 */
fun captureCertificatePin(client: OkHttpClient, url: String): Pair<String, String>? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val host = uri.host ?: return null

    // Build a one-off client that uses the same SSL stack but no pinner. Even if
    // the caller's client has a pinner, this probe ignores it so the user can
    // re-pin after a cert rotation.
    val probe = client.newBuilder()
        .certificatePinner(CertificatePinner.DEFAULT)
        .build()

    return try {
        probe.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
            val cert = resp.handshake?.peerCertificates?.firstOrNull() as? X509Certificate
                ?: return null
            host to CertificatePinner.pin(cert)
        }
    } catch (_: Throwable) {
        null
    }
}
