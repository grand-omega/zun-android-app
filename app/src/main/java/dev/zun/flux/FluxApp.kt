package dev.zun.flux

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.diag.Diagnostics
import dev.zun.flux.data.net.CertPinStore
import dev.zun.flux.data.repo.HealthRepository
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.OfflineImageCache
import dev.zun.flux.data.repo.PinnedPromptsStore
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.RealJobRepository
import dev.zun.flux.data.repo.SettingsManager
import dev.zun.flux.data.repo.UploadRepository
import dev.zun.flux.ui.auth.AuthStateHolder
import dev.zun.flux.util.isSweepableCacheFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Bundle of narrow repository interfaces that share a single backing
 * implementation. Wiring layers (e.g. [dev.zun.flux.ui.nav.AppNavHost])
 * receive this and hand each screen only the interface it actually uses.
 */
data class Repositories(
    val health: HealthRepository,
    val prompts: PromptRepository,
    val jobs: JobRepository,
    val uploads: UploadRepository,
    val images: ImageSourceRepository,
)

data class RepositoryState(
    val repositories: Repositories,
    val version: Long,
)

class FluxApp : Application() {
    private val _repositoryState = MutableStateFlow<RepositoryState?>(null)
    val repositoryState: StateFlow<RepositoryState?> = _repositoryState.asStateFlow()

    private var repositoryVersion = 0L

    val repositories: Repositories
        get() = _repositoryState.value?.repositories ?: error("Repository has not been initialized")

    var okHttpClient: OkHttpClient = OkHttpClient()
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var authStateHolder: AuthStateHolder
        private set

    lateinit var pinnedPrompts: PinnedPromptsStore
        private set

    lateinit var certPinStore: CertPinStore
        private set

    lateinit var offlineImageCache: OfflineImageCache
        private set

    val diagnostics = Diagnostics()

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        authStateHolder = AuthStateHolder(settingsManager)
        pinnedPrompts = PinnedPromptsStore(this)
        certPinStore = CertPinStore(this)

        rebuildOkHttp()
        // Resolves the client at call time so cert-pin / interceptor changes
        // via rebuildOkHttp() take effect on subsequent prefetches.
        offlineImageCache = OfflineImageCache(this, okHttpClientProvider = { okHttpClient })

        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                }
                // Memory cache holds a few full-res bitmaps so scroll-back and Telephoto
                // sub-pixel zoom don't re-decode at lower resolution.
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizeBytes(Tuning.COIL_MEMORY_CACHE_BYTES)
                        .build()
                }
                // No URL-keyed disk cache: OfflineImageCache is the canonical
                // disk store, keyed by jobId/variant. A second URL-keyed cache
                // would duplicate bytes and fight the size budget.
                .build()
        }

        rebuildRepository()

        sweepStaleCacheFiles()
    }

    /**
     * Delete one-shot cacheDir files orphaned by a crash, or by a cancellation whose cleanup
     * failed (see RealJobRepository.cancelJobUpload — the staged path is recovered from a
     * WorkManager tag, which can miss). Nothing else ever removes these, so without this they
     * accumulate until Android trims the whole cache.
     *
     * Which prefixes qualify, and why RecentInputCache's keyed store is excluded, lives with
     * the predicate in [isSweepableCacheFile].
     */
    private fun sweepStaleCacheFiles() {
        Thread {
            val cutoff = System.currentTimeMillis() - Tuning.STALE_CACHE_FILE_MAX_AGE_MS
            cacheDir.listFiles()
                ?.filter { isSweepableCacheFile(it.name, it.lastModified(), cutoff) }
                ?.forEach { it.delete() }
        }.start()
    }

    /** Rebuild OkHttpClient — called when cert pins change so the new pinner takes effect. */
    fun rebuildOkHttp() {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(Tuning.HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Tuning.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = settingsManager.apiToken ?: ""
                val request = chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                // The server content-negotiates derived images (AVIF is
                // ~30-50% smaller than JPEG); Android decodes AVIF natively.
                val path = chain.request().url.encodedPath
                if (path.endsWith("/thumb") || path.endsWith("/preview") ||
                    path.endsWith("/result") || path.endsWith("/file")
                ) {
                    request.header("Accept", "image/avif, image/jpeg;q=0.9, */*;q=0.8")
                }
                chain.proceed(request.build())
            }
            .addInterceptor(diagnostics.okHttpInterceptor())
            .certificatePinner(certPinStore.toCertificatePinner())
            .build()
    }

    /** Rebuild Retrofit when the server URL changes. */
    fun rebuildRepository() {
        val baseUrl = settingsManager.serverUrl.takeIf { !it.isNullOrBlank() } ?: "https://example.invalid"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

        val api = retrofit.create(FluxApi::class.java)
        val real = RealJobRepository(this, api, settingsManager, okHttpClient, offlineImageCache)
        _repositoryState.value = RepositoryState(
            repositories = Repositories(
                health = real,
                prompts = real,
                jobs = real,
                uploads = real,
                images = real,
            ),
            version = ++repositoryVersion,
        )
    }
}
