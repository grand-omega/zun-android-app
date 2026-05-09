package dev.zun.flux

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.diag.Diagnostics
import dev.zun.flux.data.net.CertPinStore
import dev.zun.flux.data.net.NetworkResolver
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
import io.sentry.android.core.SentryAndroid
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

    lateinit var networkResolver: NetworkResolver
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

        initSentry()

        settingsManager = SettingsManager(this)
        authStateHolder = AuthStateHolder(settingsManager)
        pinnedPrompts = PinnedPromptsStore(this)
        certPinStore = CertPinStore(this)
        networkResolver = NetworkResolver(settingsManager) { rebuildRepository() }

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

        // Re-pick LAN vs Tailscale on every default-network change. Network changes
        // must bypass the resolver's debounce cache — otherwise a Wi-Fi → cellular
        // transition shortly after a successful probe keeps the stale URL until the
        // window expires, and LAN↔Tailscale failover stops working.
        val cm = getSystemService(ConnectivityManager::class.java)
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkResolver.invalidateCache()
                networkResolver.refresh()
            }
            override fun onLost(network: Network) {
                networkResolver.invalidateCache()
                networkResolver.refresh()
            }
        })
        networkResolver.refresh()
    }

    /**
     * Initialize Sentry crash reporting. Skipped silently if SENTRY_DSN
     * wasn't provided at build time (e.g. fresh clone without a populated
     * local.properties), so the app still runs; just no crash reports flow.
     */
    private fun initSentry() {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"
            // versionName comes from `git describe`, so this tag uniquely
            // identifies which build a given crash came from.
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            // Crashes only — performance traces eat the free-tier quota fast.
            options.tracesSampleRate = 0.0
            // Don't capture screenshots / view hierarchy on crash: prompts and
            // generated images are user-content; better to opt out by default.
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
        }
    }

    /** Rebuild OkHttpClient — called when cert pins change so the new pinner takes effect. */
    fun rebuildOkHttp() {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(Tuning.HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Tuning.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = settingsManager.apiToken ?: ""
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build(),
                )
            }
            .addInterceptor(diagnostics.okHttpInterceptor())
            .certificatePinner(certPinStore.toCertificatePinner())
            .build()
    }

    /** Rebuild Retrofit when the active base URL changes. */
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
