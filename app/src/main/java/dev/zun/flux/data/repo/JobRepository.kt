package dev.zun.flux.data.repo

import dev.zun.flux.data.api.HealthResponse

/**
 * Single seam between the UI and the backend. UI layers depend on this
 * interface; implementations are swapped in [dev.zun.flux.FluxApp].
 */
interface JobRepository {
    suspend fun health(): HealthResponse
}
