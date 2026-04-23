package dev.zun.flux.data.repo

import android.net.Uri
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
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
        val id: String,
        val inputUri: Uri,
        val promptId: String,
        val promptLabel: String,
        val createdAt: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    override suspend fun health(): HealthResponse {
        delay(300)
        return HealthResponse(status = "ok (fake)")
    }

    override suspend fun listPrompts(): List<PromptDto> {
        delay(500)
        return listOf(
            PromptDto("ghibli", "Ghibli style", "Classic Studio Ghibli aesthetic"),
            PromptDto("cyberpunk", "Cyberpunk", "Neon lights and futuristic grit"),
            PromptDto("oil-painting", "Oil painting", "Rich textures and brushstrokes"),
            PromptDto("pixel-art", "Pixel art", "Retro 16-bit look"),
            PromptDto("sketch", "Pencil sketch", "Hand-drawn graphite style"),
        )
    }

    override suspend fun submitJob(
        inputUri: Uri,
        promptId: String,
    ): JobCreatedResponse {
        delay(400)
        val id = "fake-${UUID.randomUUID().toString().take(8)}"
        val label =
            when (promptId) {
                "ghibli" -> "Ghibli style"
                "cyberpunk" -> "Cyberpunk"
                "oil-painting" -> "Oil painting"
                "pixel-art" -> "Pixel art"
                "sketch" -> "Pencil sketch"
                else -> promptId
            }
        entries[id] = Entry(id, inputUri, promptId, label, System.currentTimeMillis())
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
            prompt_label = entry.promptLabel,
            progress = progress,
            error = null,
            created_at = entry.createdAt,
            completed_at =
            if (status == "done") {
                entry.createdAt + queuedDurationMs + runningDurationMs
            } else {
                null
            },
        )
    }

    override suspend fun listJobs(
        status: String,
        limit: Int,
        before: Long?,
    ): List<JobSummaryDto> {
        delay(600)
        return entries.values
            .filter { entry ->
                val elapsed = System.currentTimeMillis() - entry.createdAt
                val isDone = elapsed >= queuedDurationMs + runningDurationMs
                (status == "done" && isDone) || (status != "done")
            }.filter { entry ->
                before == null || entry.createdAt < before
            }.sortedByDescending { it.createdAt }
            .take(limit)
            .map { entry ->
                JobSummaryDto(
                    id = entry.id,
                    prompt_id = entry.promptId,
                    prompt_label = entry.promptLabel,
                    created_at = entry.createdAt,
                    duration_seconds = ((queuedDurationMs + runningDurationMs) / 1000).toInt(),
                )
            }
    }

    override suspend fun deleteJob(jobId: String) {
        delay(300)
        entries.remove(jobId)
    }

    override fun inputModel(jobId: String): Any? = entries[jobId]?.inputUri

    override fun resultModel(jobId: String): Any? = entries[jobId]?.inputUri
}
