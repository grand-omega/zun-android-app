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
        assertEquals("https://flux.example.test", normalizeOptionalServerUrl("https://flux.example.test/"))
    }

    @Test
    fun normalizeOptionalServerUrl_acceptsBareHostAndDefaultsToHttps() {
        assertEquals("https://192.168.1.10", normalizeOptionalServerUrl("192.168.1.10"))
        assertEquals("https://192.168.1.10:9000", normalizeOptionalServerUrl("192.168.1.10:9000"))
        assertEquals("https://flux.example.test", normalizeOptionalServerUrl("flux.example.test"))
    }

    @Test
    fun normalizeOptionalServerUrl_preservesExplicitSchemeAndPort() {
        assertEquals("https://192.168.1.10", normalizeOptionalServerUrl("https://192.168.1.10"))
        assertEquals("http://server.local", normalizeOptionalServerUrl("http://server.local"))
    }

    @Test
    fun normalizeOptionalServerUrl_rejectsHttpWhenDisallowed() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("http://192.168.1.10:8080", allowHttp = false)
        }
        assertEquals("Release builds require https:// server URLs", error.message)
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

    @Test
    fun normalizeOptionalServerUrl_rejectsBlockedHostRegardlessOfSchemePortOrCase() {
        val expectedMessage = "This is the production server — use your local dev server instead."

        val httpsError = assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("https://zun.h.doremysweet.com", blockHost = "zun.h.doremysweet.com")
        }
        assertEquals(expectedMessage, httpsError.message)

        val httpWithPortError = assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("http://zun.h.doremysweet.com:8443", blockHost = "zun.h.doremysweet.com")
        }
        assertEquals(expectedMessage, httpWithPortError.message)

        val mixedCaseError = assertThrows(IllegalArgumentException::class.java) {
            normalizeOptionalServerUrl("https://ZUN.H.DOREMYSWEET.COM", blockHost = "zun.h.doremysweet.com")
        }
        assertEquals(expectedMessage, mixedCaseError.message)
    }

    @Test
    fun normalizeOptionalServerUrl_acceptsOtherHostsWhenBlockHostSupplied() {
        assertEquals(
            "https://flux.example.test",
            normalizeOptionalServerUrl("https://flux.example.test", blockHost = "zun.h.doremysweet.com"),
        )
    }

    @Test
    fun normalizeOptionalServerUrl_acceptsProductionHostWhenBlockHostIsNull() {
        assertEquals(
            "https://zun.h.doremysweet.com",
            normalizeOptionalServerUrl("https://zun.h.doremysweet.com", blockHost = null),
        )
    }
}
