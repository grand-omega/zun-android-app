package dev.zun.flux.data.repo

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PinnedPromptsStoreTest {

    @Test
    fun `toggle pins and unpins`() {
        val store = PinnedPromptsStore(ApplicationProvider.getApplicationContext())
        assertTrue(store.ids.value.isEmpty())

        store.toggle(7L)
        assertEquals(setOf(7L), store.ids.value)

        store.toggle(42L)
        assertEquals(setOf(7L, 42L), store.ids.value)

        store.toggle(7L)
        assertEquals(setOf(42L), store.ids.value)
    }

    @Test
    fun `pins persist across instances`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        PinnedPromptsStore(ctx).toggle(99L)

        val reopened = PinnedPromptsStore(ctx)
        assertTrue(99L in reopened.ids.value)
        assertFalse(0L in reopened.ids.value)
    }
}
