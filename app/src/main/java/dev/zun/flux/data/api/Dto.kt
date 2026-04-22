package dev.zun.flux.data.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class PromptDto(
    val id: String,
    val label: String,
    val description: String? = null,
)

@Serializable
data class JobCreatedResponse(
    val job_id: String,
)

@Serializable
data class JobStatusDto(
    val id: String,
    val status: String,
    val prompt_id: String,
    val prompt_label: String,
    val progress: Float? = null,
    val error: String? = null,
    val created_at: Long,
    val completed_at: Long? = null,
)

@Serializable
data class JobSummaryDto(
    val id: String,
    val prompt_id: String,
    val prompt_label: String,
    val created_at: Long,
    val duration_seconds: Int? = null,
)
