package dev.zun.flux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.effectivePromptId

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val status: String,
    val inputId: Int?,
    val promptId: Long?,
    val promptText: String?,
    val workflow: String?,
    val seed: Long?,
    val progress: Float?,
    val error: String?,
    val createdAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val durationSeconds: Int?,
    val width: Int?,
    val height: Int?,
)

fun JobStatusDto.toEntity(): JobEntity = JobEntity(
    id = id,
    status = status,
    inputId = input_id,
    promptId = effectivePromptId,
    promptText = prompt_text,
    workflow = workflow,
    seed = seed,
    progress = progress,
    error = error,
    createdAt = created_at,
    startedAt = started_at,
    completedAt = completed_at,
    durationSeconds = completed_at?.let { (it - created_at).toInt() },
    width = width,
    height = height,
)

fun JobSummaryDto.toEntity(): JobEntity = JobEntity(
    id = id,
    status = status,
    inputId = input_id,
    promptId = effectivePromptId,
    promptText = prompt_text,
    workflow = workflow,
    seed = seed,
    progress = if (status == "done") 1f else null,
    error = null,
    createdAt = created_at,
    startedAt = null,
    completedAt = completed_at ?: duration_seconds?.let { created_at + it },
    durationSeconds = duration_seconds,
    width = null,
    height = null,
)

fun JobEntity.toSummaryDto(): JobSummaryDto = JobSummaryDto(
    id = id,
    status = status,
    input_id = inputId,
    source_prompt_id = promptId,
    prompt_text = promptText,
    workflow = workflow,
    seed = seed,
    created_at = createdAt,
    completed_at = completedAt,
    duration_seconds = durationSeconds,
)

fun JobEntity.toStatusDto(): JobStatusDto = JobStatusDto(
    id = id,
    status = status,
    input_id = inputId,
    source_prompt_id = promptId,
    prompt_text = promptText,
    workflow = workflow,
    seed = seed,
    progress = progress,
    error = error,
    created_at = createdAt,
    started_at = startedAt,
    completed_at = completedAt,
    width = width,
    height = height,
)
