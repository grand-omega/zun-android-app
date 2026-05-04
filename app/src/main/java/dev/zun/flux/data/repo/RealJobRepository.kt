package dev.zun.flux.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.zun.flux.data.api.FluxApi
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.local.AppDatabase
import dev.zun.flux.data.local.PendingDeleteEntity
import dev.zun.flux.data.local.toEntity
import dev.zun.flux.data.local.toStatusDto
import dev.zun.flux.data.local.toSummaryDto
import dev.zun.flux.data.worker.DeleteSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException

class RealJobRepository(
    private val context: Context,
    private val api: FluxApi,
    private val settingsManager: SettingsManager,
    okHttpClient: OkHttpClient,
) : JobRepository {
    private val dao = AppDatabase.getDatabase(context).jobDao()
    private val connectionDiagnoser = ConnectionDiagnoser(settingsManager)
    private val jobUploader = JobUploader(context, api)
    private val recentInputCache = RecentInputCache(context, api)
    private val offlineImageCache = OfflineImageCache(context, okHttpClient)
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _promptsState = MutableStateFlow<List<PromptDto>>(emptyList())
    override val promptsState: StateFlow<List<PromptDto>> = _promptsState.asStateFlow()
    private val localDeletedIds = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun health(): HealthResponse = api.health()

    override suspend fun diagnoseConnection(): ConnectionDiagnosis = connectionDiagnoser.diagnose()

    override suspend fun listPrompts(): List<PromptDto> {
        val fetched = api.listPrompts().items
        _promptsState.value = fetched
        return fetched
    }

    override suspend fun createPrompt(label: String, text: String, workflow: String): PromptDto {
        val created = api.createPrompt(
            PromptDto(id = 0L, label = label, text = text, workflow = workflow),
        )
        refreshPromptsQuietly()
        return created
    }

    override suspend fun deletePrompt(promptId: Long) {
        api.deletePrompt(promptId)
        refreshPromptsQuietly()
    }

    override suspend fun submitJob(
        inputUri: Uri,
        promptId: Long?,
        promptText: String?,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse = jobUploader.submitJob(
        inputUri = inputUri,
        promptId = promptId,
        promptText = promptText,
        workflow = workflow,
        onUploadProgress = onUploadProgress,
    )

    override suspend fun getJob(jobId: String): JobStatusDto {
        if (jobId in localDeletedIds.value || dao.isPendingDelete(jobId)) {
            error("Job was deleted")
        }
        val job = api.getJob(jobId)
        if (jobId !in localDeletedIds.value && !dao.isPendingDelete(jobId)) {
            dao.insertJob(job.toEntity())
            prefetchIfDone(job)
        }
        return job
    }

    override suspend fun listJobs(
        status: String?,
        limit: Int,
        cursor: String?,
        inputId: Int?,
    ): JobListResponse {
        val resp = api.listJobs(status, limit, cursor, inputId)
        val hiddenIds = dao.getPendingDeleteIds().toSet() + localDeletedIds.value
        val visibleItems = resp.items.filterNot { it.id in hiddenIds }
        dao.insertJobs(visibleItems.map { it.toEntity() })
        prefetchDone(visibleItems)
        return resp
    }

    override suspend fun deleteJob(jobId: String) {
        localDeletedIds.update { it + jobId }
        dao.insertPendingDelete(PendingDeleteEntity(jobId = jobId, createdAt = System.currentTimeMillis()))
        dao.deleteJobById(jobId)
        offlineImageCache.delete(jobId)
        DeleteSyncWorker.enqueue(context)
    }

    override suspend fun restoreJob(jobId: String) {
        val wasPendingLocalDelete = dao.isPendingDelete(jobId)
        if (wasPendingLocalDelete) {
            dao.deletePendingDelete(jobId)
            localDeletedIds.update { it - jobId }
            try {
                // If the worker has not flushed DELETE yet, the server still has
                // the job. Fetching it is enough to repopulate Room.
                getJob(jobId)
                return
            } catch (t: Throwable) {
                runCatching {
                    api.restoreJob(jobId)
                    getJob(jobId)
                }.onSuccess {
                    return
                }
                localDeletedIds.update { it + jobId }
                dao.insertPendingDelete(PendingDeleteEntity(jobId = jobId, createdAt = System.currentTimeMillis()))
                throw t
            }
        }
        api.restoreJob(jobId)
        localDeletedIds.update { it - jobId }
        // Repopulate local copy from server.
        getJob(jobId)
    }

    override suspend fun cancelJob(jobId: String) {
        api.cancelJob(jobId)
        // Sync the new status into Room so any open Progress screen flips to Cancelled.
        runCatching { getJob(jobId) }
    }

    override fun getJobsFlow(): Flow<List<JobSummaryDto>> = dao.getVisibleJobs().map { entities ->
        entities.filter { it.status == "done" }.map { it.toSummaryDto() }
    }

    override fun getJobFlow(jobId: String): Flow<JobStatusDto?> = dao.getVisibleJobByIdFlow(jobId).map { it?.toStatusDto() }

    override fun deletedJobIds(): Flow<Set<String>> = combine(
        dao.getPendingDeleteIdsFlow(),
        localDeletedIds,
    ) { pendingIds, deletedIds ->
        pendingIds.toSet() + deletedIds
    }

    override fun recentInputIds(limit: Int): Flow<List<Int>> = dao.getVisibleJobs().map { entities ->
        entities.asSequence()
            .mapNotNull { it.inputId }
            .distinct()
            .take(limit)
            .toList()
    }

    override suspend fun downloadInputToCache(inputId: Int): Uri = recentInputCache.downloadInputToCache(inputId)

    override fun recentInputUri(inputId: Int): Uri = recentInputCache.uri(inputId)

    override suspend fun syncHistory() {
        runCatching { syncPendingDeletes() }
        try {
            val resp = api.listJobs(status = "done", limit = 100)
            val hiddenIds = dao.getPendingDeleteIds().toSet() + localDeletedIds.value
            val visibleItems = resp.items.filterNot { it.id in hiddenIds }
            dao.insertJobs(visibleItems.map { it.toEntity() })
            prefetchDone(visibleItems)
        } catch (_: Exception) {
            // Ignore background sync errors
        }
        refreshPromptsQuietly()
    }

    override suspend fun syncPendingDeletes() {
        dao.getPendingDeletes().forEach { pendingDelete ->
            try {
                api.deleteJob(pendingDelete.jobId)
                dao.deleteJobById(pendingDelete.jobId)
                offlineImageCache.delete(pendingDelete.jobId)
                dao.deletePendingDelete(pendingDelete.jobId)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    dao.deleteJobById(pendingDelete.jobId)
                    offlineImageCache.delete(pendingDelete.jobId)
                    dao.deletePendingDelete(pendingDelete.jobId)
                } else {
                    throw e
                }
            }
        }
    }

    override fun inputModel(inputId: Int?): Any? {
        if (inputId == null) return null
        return buildUrlOrNull("/api/v1/inputs/$inputId/file")
    }

    override fun thumbModel(jobId: String): Any? = offlineImageCache.localUri(jobId, OfflineImageCache.Kind.Thumb)
        ?: buildUrlOrNull("/api/v1/jobs/$jobId/thumb")

    override fun previewModel(jobId: String): Any? = offlineImageCache.localUri(jobId, OfflineImageCache.Kind.Preview)
        ?: buildUrlOrNull("/api/v1/jobs/$jobId/preview")

    override fun resultModel(jobId: String): Any? = offlineImageCache.localUri(jobId, OfflineImageCache.Kind.Result)
        ?: buildUrlOrNull("/api/v1/jobs/$jobId/result")

    override fun offlineAvailability(jobId: String): OfflineImageAvailability = offlineImageCache.availability(jobId)

    override fun offlineCacheStats(): OfflineCacheStats = offlineImageCache.stats()

    override fun clearOfflineImageCache() {
        offlineImageCache.clear()
    }

    private fun buildUrlOrNull(path: String): String? = settingsManager.serverUrl?.takeUnless { it.isBlank() }?.let { "$it$path" }

    private fun prefetchIfDone(job: JobStatusDto) {
        if (job.status != "done") return
        prefetchJobImages(job.id)
    }

    private fun prefetchDone(jobs: List<JobSummaryDto>) {
        jobs.asSequence()
            .filter { it.status == "done" }
            .forEach { prefetchJobImages(it.id) }
    }

    private fun prefetchJobImages(jobId: String) {
        val thumbUrl = buildUrlOrNull("/api/v1/jobs/$jobId/thumb")
        val previewUrl = buildUrlOrNull("/api/v1/jobs/$jobId/preview")
        val resultUrl = buildUrlOrNull("/api/v1/jobs/$jobId/result")
        if (thumbUrl != null) {
            cacheScope.launch {
                offlineImageCache.prefetch(jobId, OfflineImageCache.Kind.Thumb, thumbUrl)
            }
        }
        if (previewUrl != null) {
            cacheScope.launch {
                offlineImageCache.prefetch(jobId, OfflineImageCache.Kind.Preview, previewUrl)
            }
        }
        if (resultUrl != null) {
            cacheScope.launch {
                offlineImageCache.prefetch(jobId, OfflineImageCache.Kind.Result, resultUrl)
            }
        }
    }

    private suspend fun refreshPromptsQuietly() {
        runCatching { listPrompts() }
            .onFailure { Log.w(TAG, "Failed to refresh prompts", it) }
    }

    private companion object {
        const val TAG = "RealJobRepository"
    }
}
