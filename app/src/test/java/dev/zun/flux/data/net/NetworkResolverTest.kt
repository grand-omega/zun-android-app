package dev.zun.flux.data.net

import dev.zun.flux.data.repo.ActiveRoute
import dev.zun.flux.data.repo.ConnectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkResolverTest {
    @Test
    fun chooseActiveRoute_autoPrefersReachableLan() {
        val chosen = chooseActiveRoute(
            mode = ConnectionMode.AUTO,
            lanUrl = LAN_URL,
            tailscaleUrl = TAILSCALE_URL,
            isLanReachable = true,
        )

        assertEquals(LAN_URL to ActiveRoute.LAN, chosen)
    }

    @Test
    fun chooseActiveRoute_autoFallsBackToTailscaleWhenLanIsUnreachable() {
        val chosen = chooseActiveRoute(
            mode = ConnectionMode.AUTO,
            lanUrl = LAN_URL,
            tailscaleUrl = TAILSCALE_URL,
            isLanReachable = false,
        )

        assertEquals(TAILSCALE_URL to ActiveRoute.TAILSCALE, chosen)
    }

    @Test
    fun chooseActiveRoute_autoUsesLanWhenItIsTheOnlyConfiguredRoute() {
        val chosen = chooseActiveRoute(
            mode = ConnectionMode.AUTO,
            lanUrl = LAN_URL,
            tailscaleUrl = null,
            isLanReachable = false,
        )

        assertEquals(LAN_URL to ActiveRoute.LAN, chosen)
    }

    @Test
    fun chooseActiveRoute_lanOnlyDoesNotFallbackToTailscale() {
        val chosen = chooseActiveRoute(
            mode = ConnectionMode.LAN_ONLY,
            lanUrl = null,
            tailscaleUrl = TAILSCALE_URL,
            isLanReachable = false,
        )

        assertNull(chosen)
    }

    @Test
    fun chooseActiveRoute_tailscaleOnlyIgnoresReachableLan() {
        val chosen = chooseActiveRoute(
            mode = ConnectionMode.TAILSCALE_ONLY,
            lanUrl = LAN_URL,
            tailscaleUrl = TAILSCALE_URL,
            isLanReachable = true,
        )

        assertEquals(TAILSCALE_URL to ActiveRoute.TAILSCALE, chosen)
    }

    private companion object {
        const val LAN_URL = "http://192.168.1.15:8080"
        const val TAILSCALE_URL = "https://zun.tailnet.example"
    }
}
