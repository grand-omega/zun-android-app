package dev.zun.flux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val status: String,
    val promptId: String,
    val promptLabel: String?,
    val customPrompt: String?,
    val progress: Float?,
    val error: String?,
    val createdAt: Long,
    val completedAt: Long?,
    val durationSeconds: Int?,
)

fun JobStatusDto.toEntity(): JobEntity = JobEntity(
    id = id,
    status = status,
    promptId = prompt_id,
    promptLabel = prompt_label,
    customPrompt = custom_prompt,
    progress = progress,
    error = error,
    createdAt = created_at,
    completedAt = completed_at,
    durationSeconds = completed_at?.let { (it - created_at).toInt() },
)

fun JobSummaryDto.toEntity(): JobEntity = JobEntity(
    id = id,
    status = "done", // Summaries only return completed jobs
    promptId = prompt_id,
    promptLabel = prompt_label,
    customPrompt = custom_prompt,
    progress = 1.0f,
    error = null,
    createdAt = created_at,
    completedAt = created_at + (duration_seconds ?: 0),
    durationSeconds = duration_seconds,
)

fun JobEntity.toSummaryDto(): JobSummaryDto = JobSummaryDto(
    id = id,
    prompt_id = promptId,
    prompt_label = promptLabel,
    custom_prompt = customPrompt,
    created_at = createdAt,
    duration_seconds = durationSeconds,
)

fun JobEntity.toStatusDto(): JobStatusDto = JobStatusDto(
    id = id,
    status = status,
    prompt_id = promptId,
    prompt_label = promptLabel,
    custom_prompt = customPrompt,
    progress = progress,
    error = error,
    created_at = createdAt,
    completed_at = completedAt,
)
