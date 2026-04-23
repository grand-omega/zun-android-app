package dev.zun.flux

import android.app.Application
import coil.Coil
import coil.ImageLoader
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

        okHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("Authorization", "Bearer ${BuildConfig.API_TOKEN}")
                            .build(),
                    )
                }.build()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(BuildConfig.SERVER_URL)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

        val api = retrofit.create(FluxApi::class.java)

        Coil.setImageLoader(
            ImageLoader
                .Builder(this)
                .okHttpClient(okHttpClient)
                .build(),
        )

        // Swapped to RealJobRepository for Milestone 9
        repository = RealJobRepository(this, api)
    }
}
