package dev.zun.flux.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerUrlsTest {
    @Test
    fun normalizeOptionalServerUrl_returnsNullForEmptyValues() {
        assertNull(normalizeOptionalServerUrl(""))
        assertNull(normalizeOptionalServerUrl("   "))
        assertNull(normalizeOptionalServerUrl("http://"))
        assertNull(normalizeOptionalServerUrl("https://"))
    }

    @Test
    fun normalizeOptionalServerUrl_removesTrailingSlashes() {
        assertEquals("http://192.168.1.10:8080", normalizeOptionalServerUrl(" http://192.168.1.10:8080/// "))
        assertEquals("https://flux.tailnet.test", normalizeOptionalServerUrl("https://flux.tailnet.test/"))
    }

    @Test
    fun normalizeOptionalLanServerUrl_acceptsBareIpv4AndDefaultsToHttps() {
        assertEquals("https://192.168.1.10", normalizeOptionalLanServerUrl("192.168.1.10"))
        assertEquals("https://192.168.1.10:9000", normalizeOptionalLanServerUrl("192.168.1.10:9000"))
    }

    @Test
    fun normalizeOptionalLanServerUrl_preservesExplicitSchemeAndPort() {
        assertEquals("https://192.168.1.10", normalizeOptionalLanServerUrl("https://192.168.1.10"))
        assertEquals("http://server.local", normalizeOptionalLanServerUrl("http://server.local"))
    }

    @Test
    fun normalizeOptionalLanServerUrl_rejectsHttpWhenDisallowed() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalLanServerUrl("http://192.168.1.10:8080", allowHttp = false)
        }
        assertEquals("Release builds require https:// server URLs", error.message)
    }

    @Test
    fun normalizeOptionalTailscaleServerUrl_defaultsBareHostToHttps() {
        assertEquals("https://zun.tailnet-name.ts.net", normalizeOptionalTailscaleServerUrl("zun.tailnet-name.ts.net"))
    }

    @Test
    fun normalizeOptionalServerUrl_rejectsUnsupportedSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("ftp://example.com")
        }
    }

    @Test
    fun normalizeOptionalServerUrl_rejectsMissingHosts() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("http:///missing-host")
        }
    }

    @Test
    fun normalizeOptionalServerUrl_rejectsQueryAndFragment() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("https://example.com?token=abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("https://example.com#section")
        }
    }
}
