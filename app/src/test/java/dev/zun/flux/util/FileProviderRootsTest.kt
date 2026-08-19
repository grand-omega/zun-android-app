package dev.zun.flux.util

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import dev.zun.flux.data.repo.localCompositeRelativePath
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Every directory whose files get handed to another app must be covered by a root in
 * res/xml/file_paths.xml. [FileProvider.getUriForFile] throws IllegalArgumentException for
 * anything outside them, and both share helpers catch broadly enough to turn that into a
 * misleading "connect to the server for uncached originals" message — so a missing root fails
 * silently rather than loudly.
 *
 * All roots are asserted in one test method on purpose: FileProvider caches its parsed
 * PathStrategy in a static map keyed by authority, while Robolectric hands each test method its
 * own data dir. Split across methods, the first one to run populates the cache and the rest then
 * resolve against a stale directory and fail for reasons that have nothing to do with the roots.
 */
@RunWith(RobolectricTestRunner::class)
class FileProviderRootsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every directory a share can reach is a configured FileProvider root`() {
        val authority = "${context.packageName}.fileprovider"
        val compositeId = "local-composite-00000000-0000-0000-0000-000000000000"
        val shareable = mapOf(
            // Feature 015: saved drag-reveal composites, shared straight from filesDir.
            "local composite" to File(context.filesDir, localCompositeRelativePath(compositeId)),
            // Feature 014: flattened composite staged in cacheDir before save/share.
            "reveal export" to File(context.cacheDir, "${REVEAL_EXPORT_CACHE_PREFIX}1.jpg"),
            // ShareUtils' copy of a remote image.
            "share copy" to File(context.cacheDir, "${SHARE_CACHE_PREFIX}1.jpg"),
            // Offline-cached result bytes, shared from the gallery.
            "offline image" to File(context.filesDir, "offline_images/job_one/result.jpg"),
        )

        shareable.forEach { (label, file) ->
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(0))
            val uri = FileProvider.getUriForFile(context, authority, file)
            assertTrue("$label: expected a content:// uri, got $uri", uri.toString().startsWith("content://$authority/"))
        }
    }
}
