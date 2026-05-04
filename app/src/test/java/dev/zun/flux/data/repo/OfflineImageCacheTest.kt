package dev.zun.flux.data.repo

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineImageCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun prefetch_writesDeterministicPrivateFile() = runTest {
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClient = imageClient { "thumb-bytes".encodeToByteArray() },
            maxBytes = 1_000,
        )

        cache.prefetch("job/one", OfflineImageCache.Kind.Thumb, "https://example.test/thumb")

        val file = temp.root.resolve("offline_images/job_one/thumb.jpg")
        assertTrue(file.exists())
        assertArrayEquals("thumb-bytes".encodeToByteArray(), file.readBytes())
    }

    @Test
    fun delete_removesCachedJobFiles() = runTest {
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClient = imageClient { "preview".encodeToByteArray() },
            maxBytes = 1_000,
        )
        cache.prefetch("job-1", OfflineImageCache.Kind.Preview, "https://example.test/preview")

        cache.delete("job-1")

        assertFalse(temp.root.resolve("offline_images/job-1/preview.jpg").exists())
    }

    @Test
    fun resultKind_usesStableResultFileName() = runTest {
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClient = imageClient { "result".encodeToByteArray() },
            maxBytes = 1_000,
        )

        cache.prefetch("job-1", OfflineImageCache.Kind.Result, "https://example.test/result")

        assertTrue(temp.root.resolve("offline_images/job-1/result.jpg").exists())
    }

    @Test
    fun prefetch_prunesOldestFilesWhenCacheExceedsLimit() = runTest {
        var counter = 0
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClient = imageClient {
                counter += 1
                ByteArray(8) { counter.toByte() }
            },
            maxBytes = 12,
        )

        cache.prefetch("old", OfflineImageCache.Kind.Thumb, "https://example.test/old")
        temp.root.resolve("offline_images/old/thumb.jpg").setLastModified(1L)
        cache.prefetch("new", OfflineImageCache.Kind.Thumb, "https://example.test/new")

        assertFalse(temp.root.resolve("offline_images/old/thumb.jpg").exists())
        assertTrue(temp.root.resolve("offline_images/new/thumb.jpg").exists())
    }

    private fun imageClient(bytes: () -> ByteArray): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bytes().toResponseBody("image/jpeg".toMediaType()))
                    .build()
            },
        )
        .build()
}
