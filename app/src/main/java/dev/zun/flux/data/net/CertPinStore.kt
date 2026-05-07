package dev.zun.flux.data.net

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.CertificatePinner

/**
 * Persistent set of certificate pins (host → "sha256/..." token in OkHttp's format).
 *
 * Self-hosted servers don't rotate certs frequently and the user controls both
 * ends — a tight pin set is realistic. Pins are captured on demand from the
 * Diagnostics panel ("Pin current certificates"), not auto-trusted, so the user
 * is explicitly in the loop.
 */
class CertPinStore(context: Context) {
    private val prefs = context.getSharedPreferences("cert_pins", Context.MODE_PRIVATE)

    private val _pins = MutableStateFlow(loadPins())
    val pins: StateFlow<Map<String, String>> = _pins.asStateFlow()

    fun setPin(host: String, pin: String) {
        val next = _pins.value.toMutableMap().apply { put(host, pin) }
        _pins.value = next
        persist(next)
    }

    fun clear(host: String) {
        val next = _pins.value.toMutableMap().apply { remove(host) }
        _pins.value = next
        persist(next)
    }

    fun clearAll() {
        _pins.value = emptyMap()
        prefs.edit { clear() }
    }

    /** Build an OkHttp [CertificatePinner] from the current pin set. */
    fun toCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        _pins.value.forEach { (host, pin) -> builder.add(host, pin) }
        return builder.build()
    }

    private fun loadPins(): Map<String, String> = prefs.all
        .mapNotNull { (k, v) -> if (v is String) k to v else null }
        .toMap()

    private fun persist(map: Map<String, String>) {
        prefs.edit {
            clear()
            map.forEach { (host, pin) -> putString(host, pin) }
        }
    }
}
