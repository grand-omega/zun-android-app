package dev.zun.flux.data.repo

import android.net.Uri
import androidx.paging.PagingData
import dev.zun.flux.data.api.CapabilitiesResponse
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.WorkflowSupportDto
import dev.zun.flux.data.api.Workflows
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spy fake: records the arguments of the last submit call and lets tests
 * gate the call's completion via [holdSubmit] or force failure via
 * [failingUris]. All other interface methods return empty/no-op stubs.
 *
 * Use this when a test cares about *what* the ViewModel asked the
 * repository to do; use [FakeJobRepository] when it cares about the
 * resulting state. They serve different test purposes — keep both.
 */
class RecordingRepository :
    JobRepository,
    HealthRepository,
    PromptRepository,
    UploadRepository,
    ImageSourceRepository {
    private val _promptsState = MutableStateFlow(
        listOf(
            PromptDto(id = 1L, label = "One"),
            PromptDto(id = 2L, label = "Two"),
        ),
    )
    override val promptsState: StateFlow<List<PromptDto>> = _promptsState.asStateFlow()

    var lastPromptId: Long? = null
        private set
    var lastPromptText: String? = null
        private set
    var lastWorkflow: String? = null
        private set
    var submitCalls = 0
        private set
    var holdSubmit: CompletableDeferred<Unit>? = null
    val failingUris = mutableSetOf<Uri>()
    var priorEditsResult: PriorEditsInfo? = null
    private var nextJobNumber = 1

    override suspend fun health(): HealthResponse = HealthResponse(status = "ok")

    override suspend fun capabilities(): CapabilitiesResponse = CapabilitiesResponse(
        workflows = listOf(
            WorkflowSupportDto(name = Workflows.DEFAULT_EDIT, default = true),
            WorkflowSupportDto(name = Workflows.TRY_HARDER_EDIT, experimental = true),
        ),
    )

    override suspend fun diagnoseConnection(): ConnectionDiagnosis = ConnectionDiagnosis.Reachable

    override suspend fun listPrompts(): List<PromptDto> = promptsState.value

    override suspend fun createPrompt(label: String, text: String, workflow: String): PromptDto {
        val prompt = PromptDto(id = 100L, label = label, text = text, workflow = workflow)
        _promptsState.value = _promptsState.value + prompt
        return prompt
    }

    override suspend fun updatePrompt(promptId: Long, label: String, text: String): PromptDto {
        val updated = _promptsState.value.first { it.id == promptId }.copy(label = label, text = text)
        _promptsState.value = _promptsState.value.map { if (it.id == promptId) updated else it }
        return updated
    }

    override suspend fun deletePrompt(promptId: Long) {
        _promptsState.value = _promptsState.value.filterNot { it.id == promptId }
    }

    override suspend fun submitJob(
        inputUri: Uri,
        selection: PromptSelection,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse {
        submitCalls++
        lastPromptId = (selection as? PromptSelection.Saved)?.promptId
        lastPromptText = (selection as? PromptSelection.Custom)?.text
        lastWorkflow = workflow
        holdSubmit?.await()
        if (inputUri in failingUris) {
            error("Upload failed")
        }
        onUploadProgress?.invoke(1f)
        return JobCreatedResponse(job_id = "job-${nextJobNumber++}", input_id = 1)
    }

    private val pendingUploads = mutableMapOf<java.util.UUID, JobUploadStatus>()

    override suspend fun enqueueJobUpload(
        inputUri: Uri,
        selection: PromptSelection,
        workflow: String?,
    ): java.util.UUID {
        val resp = submitJob(inputUri, selection, workflow, onUploadProgress = null)
        val workId = java.util.UUID.randomUUID()
        pendingUploads[workId] = JobUploadStatus.Succeeded(jobId = resp.job_id, inputId = resp.input_id)
        return workId
    }

    override fun observeJobUpload(uuid: java.util.UUID): Flow<JobUploadStatus> = MutableStateFlow(
        pendingUploads[uuid] ?: JobUploadStatus.Pending,
    )

    override suspend fun cancelJobUpload(uuid: java.util.UUID) {
        pendingUploads.remove(uuid)
    }

    override suspend fun submitStagedJob(
        filePath: String,
        selection: PromptSelection,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse = submitJob(
        inputUri = Uri.parse("file://$filePath"),
        selection = selection,
        workflow = workflow,
        onUploadProgress = onUploadProgress,
    )

    override suspend fun findPriorEdits(sha256: String): PriorEditsInfo? = priorEditsResult

    override suspend fun getJob(jobId: String, waitSeconds: Int?): JobStatusDto = JobStatusDto(
        id = jobId,
        status = "done",
        created_at = 1L,
    )

    override suspend fun listJobs(
        status: String?,
        limit: Int,
        cursor: String?,
        inputId: Int?,
    ): JobListResponse = JobListResponse(items = emptyList(), next_cursor = null)

    override suspend fun deleteJob(jobId: String) = Unit

    override suspend fun restoreJob(jobId: String) = Unit

    override suspend fun cancelJob(jobId: String) = Unit

    override fun getJobsFlow(): Flow<List<JobSummaryDto>> = MutableStateFlow(emptyList())

    override fun pagedJobs(
        promptId: Long?,
        customOnly: Boolean,
        newestFirst: Boolean,
    ): Flow<PagingData<JobSummaryDto>> = MutableStateFlow(PagingData.empty())

    override fun jobTagStats(): Flow<JobTagStats> = MutableStateFlow(
        JobTagStats(totalCount = 0, customCount = 0, perPromptCounts = emptyMap()),
    )

    override fun getJobFlow(jobId: String): Flow<JobStatusDto?> = MutableStateFlow(null)

    override fun deletedJobIds(): Flow<Set<String>> = MutableStateFlow(emptySet())

    private val activeJobIdsFlow = MutableStateFlow<List<String>>(emptyList())
    override fun activeJobIds(): Flow<List<String>> = activeJobIdsFlow.asStateFlow()

    fun setActiveJobIds(ids: List<String>) {
        activeJobIdsFlow.value = ids
    }

    override fun recentInputIds(limit: Int): Flow<List<Int>> = MutableStateFlow(emptyList())

    override suspend fun downloadInputToCache(inputId: Int): Uri = Uri.EMPTY

    override suspend fun downloadResultToCache(jobId: String): Uri = Uri.EMPTY

    override fun recentInputUri(inputId: Int): Uri = Uri.EMPTY

    override suspend fun syncHistory() = Unit

    override suspend fun syncPendingDeletes() = Unit

    override suspend fun getLineageRootId(jobId: String): String? = null

    override fun getJobsByLineageRoot(rootId: String): Flow<List<JobSummaryDto>> = MutableStateFlow(emptyList())

    override fun inputModel(inputId: Int?): Any? = null

    override fun thumbModel(jobId: String): Any? = null

    override fun previewModel(jobId: String): Any? = null

    override fun resultModel(jobId: String): Any? = null

    override fun offlineAvailability(jobId: String): OfflineImageAvailability = OfflineImageAvailability(
        thumbCached = false,
        previewCached = false,
        resultCached = false,
    )

    override val offlineCacheVersion = MutableStateFlow(0L)

    override fun offlineCacheStats(): OfflineCacheStats = OfflineCacheStats(bytes = 0L, fileCount = 0)

    override fun clearOfflineImageCache() = Unit
}
