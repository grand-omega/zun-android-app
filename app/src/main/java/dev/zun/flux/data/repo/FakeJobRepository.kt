package dev.zun.flux.data.repo

import dev.zun.flux.data.api.HealthResponse
import kotlinx.coroutines.delay

class FakeJobRepository : JobRepository {
    override suspend fun health(): HealthResponse {
        delay(500)
        return HealthResponse(status = "ok (fake)")
    }
}
