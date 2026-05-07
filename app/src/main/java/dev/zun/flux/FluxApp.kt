package dev.zun.flux

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.diag.Diagnostics
import dev.zun.flux.data.net.CertPinStore
import dev.zun.flux.data.net.NetworkResolver
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.PinnedPromptsStore
import dev.zun.flux.data.repo.RealJobRepository
import dev.zun.flux.data.repo.SettingsManager
import dev.zun.flux.ui.auth.AuthStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

data class RepositoryState(
    val repository: JobRepository,
    val version: Long,
)

class FluxApp : Application() {
    private val _repositoryState = MutableStateFlow<RepositoryState?>(null)
    val repositoryState: StateFlow<RepositoryState?> = _repositoryState.asStateFlow()

    private var repositoryVersion = 0L

    val repository: JobRepository
        get() = _repositoryState.value?.repository ?: error("Repository has not been initialized")

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

    val diagnostics = Diagnostics()

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        authStateHolder = AuthStateHolder(settingsManager)
        pinnedPrompts = PinnedPromptsStore(this)
        certPinStore = CertPinStore(this)
        networkResolver = NetworkResolver(settingsManager) { rebuildRepository() }

        rebuildOkHttp()

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                }
                // OfflineImageCache already persists thumb/preview/result to disk; Coil's
                // disk cache would just double-bookkeep. Keep a small memory cache so
                // recently-shown tiles don't decode again on scroll.
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizeBytes(Tuning.COIL_MEMORY_CACHE_BYTES)
                        .build()
                }
                .diskCache(null as DiskCache?)
                .build()
        }

        rebuildRepository()

        // Re-pick LAN vs Tailscale on every default-network change.
        val cm = getSystemService(ConnectivityManager::class.java)
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkResolver.refresh()
            }
            override fun onLost(network: Network) {
                networkResolver.refresh()
            }
        })
        networkResolver.refresh()
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
        _repositoryState.value = RepositoryState(
            repository = RealJobRepository(this, api, settingsManager, okHttpClient),
            version = ++repositoryVersion,
        )
    }
}
