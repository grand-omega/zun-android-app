package dev.zun.flux.util

import dev.zun.flux.data.repo.RECENT_INPUT_CACHE_PREFIX
import dev.zun.flux.data.repo.UPLOAD_STAGED_CACHE_PREFIX
import dev.zun.flux.ui.capture.CAPTURE_CACHE_PREFIX
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [isSweepableCacheFile] — see FluxApp.sweepStaleCacheFiles. */
class CacheSweepTest {
    private val cutoff = 1_000L
    private val old = cutoff - 1
    private val fresh = cutoff + 1

    private fun sweepable(name: String, lastModified: Long = old) = isSweepableCacheFile(name, lastModified, cutoff)

    @Test
    fun `sweeps every one-shot prefix once past the cutoff`() {
        assertTrue(sweepable("${UPLOAD_PREPROCESSED_CACHE_PREFIX}1.jpg"))
        assertTrue(sweepable("${UPLOAD_STAGED_CACHE_PREFIX}img_1.jpg"))
        assertTrue(sweepable("${SHARE_CACHE_PREFIX}1.jpg"))
        assertTrue(sweepable("${REVEAL_EXPORT_CACHE_PREFIX}1.jpg"))
        assertTrue(sweepable("${CAPTURE_CACHE_PREFIX}1.jpg"))
        assertTrue(sweepable("${LOCAL_INPUT_CACHE_PREFIX}1.jpg"))
    }

    @Test
    fun `staged uploads are swept - the orphan the old sweep documented but missed`() {
        assertTrue(sweepable("${UPLOAD_STAGED_CACHE_PREFIX}content_42_99.jpg"))
    }

    @Test
    fun `never sweeps RecentInputCache despite input_ being a prefix of input_recent_`() {
        val keyed = "${RECENT_INPUT_CACHE_PREFIX}42.jpg"
        // The hazard this guards: the name matches LOCAL_INPUT_CACHE_PREFIX too, so a sweep that
        // checked sweepable prefixes first would delete a live, re-read cache entry.
        assertTrue(keyed.startsWith(LOCAL_INPUT_CACHE_PREFIX))
        assertFalse(sweepable(keyed))
        assertFalse(sweepable(keyed, lastModified = 0L))
    }

    @Test
    fun `leaves files younger than the cutoff alone`() {
        assertFalse(sweepable("${SHARE_CACHE_PREFIX}1.jpg", lastModified = fresh))
        assertFalse(sweepable("${CAPTURE_CACHE_PREFIX}1.jpg", lastModified = cutoff))
    }

    @Test
    fun `leaves unrecognized names alone`() {
        assertFalse(sweepable("some_other_file.jpg"))
        assertFalse(sweepable(""))
    }
}
