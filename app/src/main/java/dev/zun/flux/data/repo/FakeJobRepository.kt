package dev.zun.flux.data.repo

import android.net.Uri
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
        val inputId: Int,
        val promptId: Long?,
        val promptText: String?,
        val workflow: String?,
        val createdAt: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()
    private val updates = MutableStateFlow(0)
    private val nextInputId = AtomicInteger(1)

    private val fakePrompts = listOf(
        PromptDto(1L, "Ghibli style", "Classic Studio Ghibli aesthetic", workflow = "flux2_klein_edit"),
        PromptDto(2L, "Cyberpunk", "Neon lights and futuristic grit", workflow = "flux2_klein_edit"),
        PromptDto(3L, "Oil painting", "Rich textures and brushstrokes", workflow = "flux2_klein_edit"),
        PromptDto(4L, "Pixel art", "Retro 16-bit look", workflow = "flux2_klein_edit"),
        PromptDto(5L, "Pencil sketch", "Hand-drawn graphite style", workflow = "flux2_klein_edit"),
    )

    private val _promptsState = MutableStateFlow<List<PromptDto>>(emptyList())
    override val promptsState: StateFlow<List<PromptDto>> = _promptsState.asStateFlow()

    override suspend fun health(): HealthResponse {
        delay(300)
        return HealthResponse(status = "ok (fake)")
    }

    private val extraPrompts = MutableStateFlow<List<PromptDto>>(emptyList())
    private val nextPromptId = AtomicInteger(100)

    override suspend fun listPrompts(): List<PromptDto> {
        delay(500)
        val all = fakePrompts + extraPrompts.value
        _promptsState.value = all
        return all
    }

    override suspend fun createPrompt(label: String, text: String, workflow: String): PromptDto {
        val id = nextPromptId.getAndIncrement().toLong()
        val created = PromptDto(id = id, label = label, text = text, workflow = workflow)
        extraPrompts.value = extraPrompts.value + created
        listPrompts()
        return created
    }

    override suspend fun deletePrompt(promptId: Long) {
        extraPrompts.value = extraPrompts.value.filterNot { it.id == promptId }
        listPrompts()
    }

    override suspend fun submitJob(
        inputUri: Uri,
        promptId: Long?,
        promptText: String?,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse {
        if (onUploadProgress != null) {
            for (i in 1..10) {
                delay(100)
                onUploadProgress(i.toFloat() / 10f)
            }
        }
        delay(400)
        val id = "fake-${UUID.randomUUID().toString().take(8)}"
        val inputId = nextInputId.getAndIncrement()
        entries[id] = Entry(
            id = id,
            inputUri = inputUri,
            inputId = inputId,
            promptId = promptId,
            promptText = promptText,
            workflow = workflow ?: fakePrompts.firstOrNull { it.id == promptId }?.workflow,
            createdAt = System.currentTimeMillis() / 1000,
        )
        updates.value++
        return JobCreatedResponse(job_id = id, input_id = inputId)
    }

    override suspend fun getJob(jobId: String): JobStatusDto {
        val entry = entries[jobId] ?: error("Unknown fake job: $jobId")
        val isCancelled = cancelledIds.contains(jobId)
        val elapsedSeconds = (System.currentTimeMillis() / 1000) - entry.createdAt
        val status =
            when {
                isCancelled -> "cancelled"
                elapsedSeconds < (queuedDurationMs / 1000) -> "queued"
                elapsedSeconds < ((queuedDurationMs + runningDurationMs) / 1000) -> "running"
                else -> "done"
            }
        val progress =
            when (status) {
                "running" ->
                    ((elapsedSeconds * 1000 - queuedDurationMs).toFloat() / runningDurationMs)
                        .coerceIn(0f, 1f)
                "done" -> 1f
                else -> null
            }
        return JobStatusDto(
            id = jobId,
            status = status,
            input_id = entry.inputId,
            prompt_id = entry.promptId,
            prompt_text = entry.promptText,
            workflow = entry.workflow,
            seed = null,
            progress = progress,
            error = null,
            created_at = entry.createdAt,
            started_at = if (status != "queued") entry.createdAt + (queuedDurationMs / 1000) else null,
            completed_at = if (status == "done") {
                entry.createdAt + (queuedDurationMs + runningDurationMs) / 1000
            } else {
                null
            },
        )
    }

    override suspend fun listJobs(
        status: String?,
        limit: Int,
        cursor: String?,
        inputId: Int?,
    ): JobListResponse {
        delay(600)
        val nowSeconds = System.currentTimeMillis() / 1000
        val items = entries.values
            .filter { entry ->
                val elapsedSeconds = nowSeconds - entry.createdAt
                val isDone = elapsedSeconds >= (queuedDurationMs + runningDurationMs) / 1000
                status == null || (status == "done" && isDone) || (status != "done")
            }.filter { entry ->
                inputId == null || entry.inputId == inputId
            }.sortedByDescending { it.createdAt }
            .take(limit)
            .map { entry ->
                JobSummaryDto(
                    id = entry.id,
                    status = "done",
                    input_id = entry.inputId,
                    prompt_id = entry.promptId,
                    prompt_text = entry.promptText,
                    workflow = entry.workflow,
                    seed = null,
                    created_at = entry.createdAt,
                    completed_at = entry.createdAt + (queuedDurationMs + runningDurationMs) / 1000,
                    duration_seconds = ((queuedDurationMs + runningDurationMs) / 1000).toInt(),
                )
            }
        return JobListResponse(items = items, next_cursor = null)
    }

    override suspend fun deleteJob(jobId: String) {
        delay(300)
        entries.remove(jobId)
        updates.value++
    }

    override suspend fun restoreJob(jobId: String) {
        // Fake doesn't model soft delete
    }

    override suspend fun cancelJob(jobId: String) {
        cancelledIds.add(jobId)
        updates.value++
    }

    override fun getJobsFlow(): Flow<List<JobSummaryDto>> = updates.map {
        listJobs(status = "done", limit = 100, cursor = null, inputId = null).items
    }

    override fun getJobFlow(jobId: String): Flow<JobStatusDto?> = updates.map {
        try {
            getJob(jobId)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun syncHistory() {
        // No-op for fake
    }

    override fun inputModel(inputId: Int?): Any? = inputId?.let { id ->
        entries.values.firstOrNull { it.inputId == id }?.inputUri
    }

    override fun thumbModel(jobId: String): Any? = entries[jobId]?.inputUri

    override fun previewModel(jobId: String): Any? = entries[jobId]?.inputUri

    override fun resultModel(jobId: String): Any? = entries[jobId]?.inputUri
}
