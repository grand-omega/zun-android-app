package dev.zun.flux

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.RealJobRepository
import dev.zun.flux.data.repo.SettingsManager
import dev.zun.flux.ui.auth.AuthStateHolder
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class FluxApp : Application() {
    lateinit var repository: JobRepository
        private set

    lateinit var okHttpClient: OkHttpClient
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var authStateHolder: AuthStateHolder
        private set

    override fun onCreate() {
        super.onCreate()

        settingsManager = SettingsManager(this)
        authStateHolder = AuthStateHolder(settingsManager)

        // Interceptor that reads the current token from settings
        okHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val token = settingsManager.apiToken ?: ""
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build(),
                    )
                }.build()

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                }
                .build()
        }

        rebuildRepository()
    }

    /**
     * Call this when serverUrl or apiToken changes to refresh the repository.
     */
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
        repository = RealJobRepository(this, api, settingsManager)
    }
}
