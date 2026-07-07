package dev.zun.flux.data.repo

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
            okHttpClientProvider = { imageClient { "thumb-bytes".encodeToByteArray() } },
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
            okHttpClientProvider = { imageClient { "preview".encodeToByteArray() } },
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
            okHttpClientProvider = { imageClient { "result".encodeToByteArray() } },
            maxBytes = 1_000,
        )

        cache.prefetch("job-1", OfflineImageCache.Kind.Result, "https://example.test/result")

        assertTrue(temp.root.resolve("offline_images/job-1/result.jpg").exists())
    }

    @Test
    fun statsAndClear_reportAndRemoveCachedFiles() = runTest {
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClientProvider = { imageClient { "cached".encodeToByteArray() } },
            maxBytes = 1_000,
        )
        cache.prefetch("job-1", OfflineImageCache.Kind.Result, "https://example.test/result")

        val stats = cache.stats()
        assertTrue(stats.fileCount > 0)
        assertTrue(stats.bytes > 0)

        cache.clear()

        assertEquals(0, cache.stats().fileCount)
        assertEquals(0L, cache.stats().bytes)
    }

    @Test
    fun prefetch_prunesOldestFilesWhenCacheExceedsLimit() = runTest {
        var counter = 0
        val client = imageClient {
            counter += 1
            ByteArray(8) { counter.toByte() }
        }
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClientProvider = { client },
            maxBytes = 12,
            pruneEvery = 1,
        )

        cache.prefetch("old", OfflineImageCache.Kind.Thumb, "https://example.test/old")
        temp.root.resolve("offline_images/old/thumb.jpg").setLastModified(1L)
        cache.prefetch("new", OfflineImageCache.Kind.Thumb, "https://example.test/new")

        assertFalse(temp.root.resolve("offline_images/old/thumb.jpg").exists())
        assertTrue(temp.root.resolve("offline_images/new/thumb.jpg").exists())
    }

    @Test
    fun listCachedJobs_reportsOneEntryPerJobWithItsCachedKindsAndBytes() = runTest {
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClientProvider = { imageClient { "12345678".encodeToByteArray() } },
            maxBytes = 10_000,
        )
        cache.prefetch("job-1", OfflineImageCache.Kind.Thumb, "https://example.test/thumb")
        cache.prefetch("job-1", OfflineImageCache.Kind.Preview, "https://example.test/preview")
        cache.prefetch("job-2", OfflineImageCache.Kind.Result, "https://example.test/result")

        val summaries = cache.listCachedJobs().associateBy { it.jobId }

        assertEquals(2, summaries.size)
        assertEquals(setOf(OfflineImageCache.Kind.Thumb, OfflineImageCache.Kind.Preview), summaries.getValue("job-1").cachedKinds)
        assertEquals(16L, summaries.getValue("job-1").bytes)
        assertEquals(setOf(OfflineImageCache.Kind.Result), summaries.getValue("job-2").cachedKinds)
        assertEquals(8L, summaries.getValue("job-2").bytes)
    }

    @Test
    fun listCachedJobs_isEmptyWhenNothingIsCached() = runTest {
        val cache = OfflineImageCache(
            rootDir = temp.newFolder("offline_images"),
            okHttpClientProvider = { imageClient { ByteArray(0) } },
            maxBytes = 10_000,
        )

        assertEquals(emptyList<OfflineImageCache.CachedJobSummary>(), cache.listCachedJobs())
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
