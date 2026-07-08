package dev.zun.flux.data.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String? = null,
    val disk: DiskHealth? = null,
)

@Serializable
data class DiskHealth(
    val data_bytes: Long? = null,
)

@Serializable
data class CapabilitiesResponse(
    val version: String? = null,
    val workflows: List<WorkflowSupportDto> = emptyList(),
)

@Serializable
data class WorkflowSupportDto(
    val name: String,
    val display_name: String? = null,
    val supported: Boolean = true,
    val default: Boolean = false,
    val experimental: Boolean = false,
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
data class PolishPromptRequest(
    val text: String,
)

@Serializable
data class PolishPromptResponse(
    val text: String,
)

@Serializable
data class JobStatusDto(
    val id: String,
    val status: String,
    val input_id: Int? = null,
    val source_prompt_id: Long? = null,
    val prompt_text: String? = null,
    val workflow: String? = null,
    val seed: Long? = null,
    val progress: Float? = null,
    /** Queued jobs the worker picks first; 0 = next up. Null unless queued. */
    val queue_position: Int? = null,
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
    val source_prompt_id: Long? = null,
    val prompt_text: String? = null,
    val workflow: String? = null,
    val seed: Long? = null,
    val created_at: Long,
    val completed_at: Long? = null,
    val duration_seconds: Int? = null,
    // Local-only; the server never sends this. Populated from JobEntity when
    // this DTO is used as the in-memory gallery/photo-viewer representation.
    val isFavorite: Boolean = false,
    // Local-only, computed at query time; the server never sends this. How many done jobs
    // share this job's lineage root under the currently-applied gallery filters. 1 means "no
    // stack" (no badge shown).
    val stackCount: Int = 1,
    // Local-only, computed at query time. True when any job in this stack (cover or not) is
    // favorited — lets the gallery show a partial-favorite cue on a stack whose own cover isn't
    // the favorited member, without implying the whole stack was favorited.
    val stackHasFavorite: Boolean = false,
)

@Serializable
data class JobListResponse(
    val items: List<JobSummaryDto>,
    val next_cursor: String? = null,
)

val JobStatusDto.effectivePromptId: Long?
    get() = source_prompt_id

val JobSummaryDto.effectivePromptId: Long?
    get() = source_prompt_id

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
