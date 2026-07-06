package dev.zun.flux.data.repo

import android.net.Uri
import dev.zun.flux.data.api.JobCreatedResponse
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** A match found by [UploadRepository.findPriorEdits]: the lineage group and how many prior edits it holds. */
data class PriorEditsInfo(val lineageRootId: String, val editCount: Int)

/** Outcome stream for a background upload enqueued via WorkManager. */
sealed interface JobUploadStatus {
    data object Pending : JobUploadStatus
    data class InProgress(val progress: Float) : JobUploadStatus
    data class Succeeded(val jobId: String, val inputId: Int?) : JobUploadStatus
    data class Failed(val message: String) : JobUploadStatus
}

interface UploadRepository {
    /**
     * Submit a job. [workflow] is required when [selection] is
     * [PromptSelection.Custom] (the server has no default workflow for
     * free-text prompts).
     *
     * Uses the v2 JSON-probe-then-multipart flow: we try a cheap JSON submit first
     * with only the sha256 hash; the server returns 409 `need_upload` if it doesn't
     * have the bytes cached, at which point we re-submit as multipart.
     */
    suspend fun submitJob(
        inputUri: Uri,
        selection: PromptSelection,
        workflow: String? = null,
        onUploadProgress: ((Float) -> Unit)? = null,
    ): JobCreatedResponse

    /**
     * Stages the input image into private cache and enqueues a WorkManager
     * upload. The image preprocessing (down-scale + EXIF rotate) happens
     * synchronously before this returns so the file outlives the URI grant.
     * Returns the work request UUID — pass it to [observeJobUpload] for
     * progress and outcome.
     *
     * Pass [knownSourceInputId] when [inputUri] is a re-download of a
     * previously-uploaded recent photo (not a fresh gallery pick), so the
     * new job's lineage is tied to that original input directly instead of
     * being independently re-derived from a hash of the re-staged file —
     * re-encoding doesn't reliably reproduce the exact same bytes.
     */
    suspend fun enqueueJobUpload(
        inputUri: Uri,
        selection: PromptSelection,
        workflow: String? = null,
        knownSourceInputId: Int? = null,
    ): UUID

    /** Observe progress and outcome of a prior [enqueueJobUpload]. */
    fun observeJobUpload(uuid: UUID): Flow<JobUploadStatus>

    /**
     * Cancels a pending or running upload enqueued via [enqueueJobUpload] and
     * deletes its staged file if still present. Idempotent.
     */
    suspend fun cancelJobUpload(uuid: UUID)

    /**
     * Submit a previously-staged file. Used by [dev.zun.flux.data.worker.JobUploadWorker];
     * UI code should call [enqueueJobUpload] instead.
     */
    suspend fun submitStagedJob(
        filePath: String,
        selection: PromptSelection,
        workflow: String? = null,
        onUploadProgress: ((Float) -> Unit)? = null,
        knownSourceInputId: Int? = null,
    ): JobCreatedResponse

    /**
     * Looks up whether [sha256] (a source image's content hash) matches the
     * source or result of any prior successfully-completed job. Returns
     * `null` if there's no match — this photo hasn't been edited before.
     */
    suspend fun findPriorEdits(sha256: String): PriorEditsInfo?
}
