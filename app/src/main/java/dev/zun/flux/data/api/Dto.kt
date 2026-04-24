package dev.zun.flux.data.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class PromptDto(
    val id: Long,
    val label: String,
    val description: String? = null,
    val text: String? = null,
    val workflow: String? = null,
    val timeout_seconds: Int? = null,
    val created_at: Long? = null,
    val updated_at: Long? = null,
)

@Serializable
data class PromptListResponse(
    val items: List<PromptDto>,
)

@Serializable
data class JobSubmitRequest(
    val input_sha256: String,
    val input_name: String? = null,
    val prompt_id: Long? = null,
    val prompt_text: String? = null,
    val workflow: String? = null,
)

@Serializable
data class JobCreatedResponse(
    val job_id: String,
    val input_id: Int? = null,
)

@Serializable
data class NeedUploadResponse(
    val code: String? = null,
    val need_upload: Boolean = false,
    val input_id: Int? = null,
)

@Serializable
data class JobStatusDto(
    val id: String,
    val status: String,
    val input_id: Int? = null,
    val prompt_id: Long? = null,
    val prompt_text: String? = null,
    val workflow: String? = null,
    val seed: Long? = null,
    val progress: Float? = null,
    val error: String? = null,
    val created_at: Long,
    val started_at: Long? = null,
    val completed_at: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class JobSummaryDto(
    val id: String,
    val status: String = "done",
    val input_id: Int? = null,
    val prompt_id: Long? = null,
    val prompt_text: String? = null,
    val workflow: String? = null,
    val seed: Long? = null,
    val created_at: Long,
    val completed_at: Long? = null,
    val duration_seconds: Int? = null,
)

@Serializable
data class JobListResponse(
    val items: List<JobSummaryDto>,
    val next_cursor: String? = null,
)

@Serializable
data class InputMetadata(
    val id: Int,
    val sha256: String,
    val available: Boolean,
    val original_name: String? = null,
    val content_type: String? = null,
    val size_bytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val created_at: Long,
    val last_used_at: Long? = null,
)
