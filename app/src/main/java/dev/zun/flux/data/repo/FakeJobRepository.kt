package dev.zun.flux.data.repo

import android.net.Uri
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobStatusDto
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory mock. Simulates job progression as a function of elapsed time:
 * queued (< 1s) → running (< [runningDurationMs]) → done.
 */
class FakeJobRepository(
    private val queuedDurationMs: Long = 1_000,
    private val runningDurationMs: Long = 6_000,
) : JobRepository {
    private data class Entry(
        val inputUri: Uri,
        val promptId: String,
        val createdAt: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    override suspend fun health(): HealthResponse {
        delay(300)
        return HealthResponse(status = "ok (fake)")
    }

    override suspend fun submitJob(
        inputUri: Uri,
        promptId: String,
    ): JobCreatedResponse {
        delay(400)
        val id = "fake-${UUID.randomUUID().toString().take(8)}"
        entries[id] = Entry(inputUri, promptId, System.currentTimeMillis())
        return JobCreatedResponse(job_id = id)
    }

    override suspend fun getJob(jobId: String): JobStatusDto {
        val entry = entries[jobId] ?: error("Unknown fake job: $jobId")
        val elapsed = System.currentTimeMillis() - entry.createdAt
        val status =
            when {
                elapsed < queuedDurationMs -> "queued"
                elapsed < queuedDurationMs + runningDurationMs -> "running"
                else -> "done"
            }
        val progress =
            when (status) {
                "running" ->
                    ((elapsed - queuedDurationMs).toFloat() / runningDurationMs)
                        .coerceIn(0f, 1f)
                "done" -> 1f
                else -> null
            }
        return JobStatusDto(
            id = jobId,
            status = status,
            prompt_id = entry.promptId,
            prompt_label = entry.promptId,
            progress = progress,
            error = null,
            created_at = entry.createdAt,
            completed_at = if (status == "done") entry.createdAt + queuedDurationMs + runningDurationMs else null,
        )
    }

    override fun resultModel(jobId: String): Any? = entries[jobId]?.inputUri
}
