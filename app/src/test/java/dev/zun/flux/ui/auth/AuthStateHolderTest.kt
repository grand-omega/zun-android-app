package dev.zun.flux.ui.auth

import androidx.test.core.app.ApplicationProvider
import dev.zun.flux.data.repo.SettingsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthStateHolderTest {
    private val lockoutMs = 60_000L

    private fun holderWithLastAuth(offsetFromNowMs: Long): AuthStateHolder {
        val settings = SettingsManager(ApplicationProvider.getApplicationContext())
        settings.lockoutDurationMs = lockoutMs
        settings.lastAuthTimestamp = System.currentTimeMillis() + offsetFromNowMs
        return AuthStateHolder(settings)
    }

    @Test
    fun `stays unlocked inside the grace window`() {
        assertTrue(holderWithLastAuth(-lockoutMs / 2).isAuthed)
    }

    @Test
    fun `locks once the grace window has passed`() {
        assertFalse(holderWithLastAuth(-lockoutMs * 2).isAuthed)
    }

    @Test
    fun `locks when the device clock has moved backwards past the last unlock`() {
        // Winding the clock back leaves lastAuthTimestamp in the future, so elapsed goes negative.
        // Compared with a bare `elapsed <= lockoutDurationMs` that reads as "well within the
        // window" and skips the biometric prompt entirely — an offline bypass of the app lock.
        assertFalse(holderWithLastAuth(offsetFromNowMs = 365L * 24 * 60 * 60 * 1000).isAuthed)
    }

    @Test
    fun `locks when there is no recorded unlock`() {
        val settings = SettingsManager(ApplicationProvider.getApplicationContext())
        settings.lockoutDurationMs = lockoutMs
        settings.lastAuthTimestamp = 0L
        assertFalse(AuthStateHolder(settings).isAuthed)
    }

    @Test
    fun `markAuthed unlocks and checkLock keeps it unlocked inside the window`() {
        val holder = holderWithLastAuth(-lockoutMs * 2)
        assertFalse(holder.isAuthed)

        holder.markAuthed()
        assertTrue(holder.isAuthed)

        holder.checkLock()
        assertTrue(holder.isAuthed)
    }
}
