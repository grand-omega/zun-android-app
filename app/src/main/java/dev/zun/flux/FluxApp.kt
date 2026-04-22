package dev.zun.flux

import android.app.Application
import coil.Coil
import coil.ImageLoader
import dev.zun.flux.data.repo.FakeJobRepository
import dev.zun.flux.data.repo.JobRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FluxApp : Application() {

    lateinit var repository: JobRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer ${BuildConfig.API_TOKEN}")
                        .build()
                )
            }
            .build()

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(okHttp)
                .build()
        )

        // Milestone 1: fake repo. Swap to RealJobRepository(okHttp) when server is up.
        repository = FakeJobRepository()
    }
}
