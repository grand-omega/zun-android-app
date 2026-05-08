package dev.zun.flux.ui.home

import android.net.Uri
import androidx.paging.PagingData
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.ConnectionDiagnosis
import dev.zun.flux.data.repo.HealthRepository
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.JobTagStats
import dev.zun.flux.data.repo.JobUploadStatus
import dev.zun.flux.data.repo.OfflineCacheStats
import dev.zun.flux.data.repo.OfflineImageAvailability
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.PromptSelection
import dev.zun.flux.data.repo.UploadRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: RecordingRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        repository = RecordingRepository()
        viewModel = HomeViewModel(
            healthRepo = repository,
            promptRepo = repository,
            jobRepo = repository,
            uploadRepo = repository,
        )
    }

    @Test
    fun addInputUris_capsAndDedupesInputs() = runTest {
        runCurrent()

        val result = viewModel.addInputUris(listOf(Uri.EMPTY, Uri.EMPTY), maxImages = 1)

        assertTrue(result.capped)
        assertEquals(listOf(Uri.EMPTY), viewModel.composer.value.inputUris)
    }

    @Test
    fun deleteSelectedPrompt_clearsSelection() = runTest {
        runCurrent()
        viewModel.selectPrompt(1L)

        viewModel.deletePrompt(1L)
        advanceUntilIdle()

        assertNull(viewModel.composer.value.selectedPromptId)
    }

    @Test
    fun savePrompt_selectsCreatedPromptAndClearsCustomText() = runTest {
        runCurrent()
        viewModel.updateCustomPrompt("make it cinematic")

        viewModel.savePrompt("Cinematic", "make it cinematic")
        advanceUntilIdle()

        assertEquals(100L, viewModel.composer.value.selectedPromptId)
        assertEquals("", viewModel.composer.value.customPromptText)
    }

    @Test
    fun submit_usesPromptIdWhenSavedPromptSelected() = runTest {
        runCurrent()
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(2L)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(2L, repository.lastPromptId)
        assertNull(repository.lastPromptText)
    }

    @Test
    fun submit_usesPromptTextWhenCustomPromptSelected() = runTest {
        runCurrent()
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(CUSTOM_PROMPT_ID)
        viewModel.updateCustomPrompt("make it brighter")

        viewModel.submit()
        advanceUntilIdle()

        assertNull(repository.lastPromptId)
        assertEquals("make it brighter", repository.lastPromptText)
    }

    @Test
    fun acknowledgeDone_clearsInputsAfterSuccessfulSubmit() = runTest {
        runCurrent()
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(2L)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.acknowledgeDone()

        assertEquals(emptyList<Uri>(), viewModel.composer.value.inputUris)
    }

    @Test
    fun submit_tryHarderCustomPromptUsesExperimentalWorkflow() = runTest {
        runCurrent()
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(CUSTOM_PROMPT_ID)
        viewModel.updateCustomPrompt("make it sharper")
        viewModel.setTryHarder(true)

        viewModel.submit()
        advanceUntilIdle()

        assertNull(repository.lastPromptId)
        assertEquals("make it sharper", repository.lastPromptText)
        assertEquals("flux2_klein_9b_kv_experimental", repository.lastWorkflow)
    }

    @Test
    fun submit_customPromptUsesDefaultWorkflowWhenTryHarderIsOff() = runTest {
        runCurrent()
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(CUSTOM_PROMPT_ID)
        viewModel.updateCustomPrompt("make it brighter")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("flux2_klein_edit", repository.lastWorkflow)
    }

    @Test
    fun submit_failedSingleUploadKeepsInputsForRetry() = runTest {
        runCurrent()
        repository.failingUris += Uri.EMPTY
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(2L)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(SubmitState.Failed("Couldn't submit: Upload failed"), viewModel.state.value)
        assertEquals(listOf(Uri.EMPTY), viewModel.composer.value.inputUris)
        assertNull(viewModel.uploadProgress.value)
        assertNull(viewModel.batchProgress.value)
    }

    @Test
    fun submit_ignoresSecondSubmitWhileInFlight() = runTest {
        runCurrent()
        val holdSubmit = CompletableDeferred<Unit>()
        repository.holdSubmit = holdSubmit
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(2L)

        viewModel.submit()
        runCurrent()
        viewModel.submit()

        assertEquals(1, repository.submitCalls)
        assertTrue(viewModel.state.value is SubmitState.InFlight)

        holdSubmit.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun acknowledgeDone_doesNotClearInputsAfterFailedSubmit() = runTest {
        runCurrent()
        repository.failingUris += Uri.EMPTY
        viewModel.addInputUris(listOf(Uri.EMPTY), maxImages = 20)
        viewModel.selectPrompt(2L)

        viewModel.submit()
        advanceUntilIdle()
        viewModel.acknowledgeDone()

        assertEquals(listOf(Uri.EMPTY), viewModel.composer.value.inputUris)
        assertEquals(SubmitState.Idle, viewModel.state.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class RecordingRepository :
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
    private var nextJobNumber = 1

    override suspend fun health(): HealthResponse = HealthResponse(status = "ok")

    override suspend fun diagnoseConnection(): ConnectionDiagnosis = ConnectionDiagnosis.Reachable

    override suspend fun listPrompts(): List<PromptDto> = promptsState.value

    override suspend fun createPrompt(label: String, text: String, workflow: String): PromptDto {
        val prompt = PromptDto(id = 100L, label = label, text = text, workflow = workflow)
        _promptsState.value = _promptsState.value + prompt
        return prompt
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

    override suspend fun getJob(jobId: String): JobStatusDto = JobStatusDto(
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
    ): Flow<PagingData<JobSummaryDto>> = MutableStateFlow(PagingData.empty())

    override fun jobTagStats(): Flow<JobTagStats> = MutableStateFlow(
        JobTagStats(totalCount = 0, customCount = 0, perPromptCounts = emptyMap()),
    )

    override fun getJobFlow(jobId: String): Flow<JobStatusDto?> = MutableStateFlow(null)

    override fun deletedJobIds(): Flow<Set<String>> = MutableStateFlow(emptySet())

    override fun recentInputIds(limit: Int): Flow<List<Int>> = MutableStateFlow(emptyList())

    override suspend fun downloadInputToCache(inputId: Int): Uri = Uri.EMPTY

    override fun recentInputUri(inputId: Int): Uri = Uri.EMPTY

    override suspend fun syncHistory() = Unit

    override suspend fun syncPendingDeletes() = Unit

    override fun inputModel(inputId: Int?): Any? = null

    override fun thumbModel(jobId: String): Any? = null

    override fun previewModel(jobId: String): Any? = null

    override fun resultModel(jobId: String): Any? = null

    override fun offlineAvailability(jobId: String): OfflineImageAvailability = OfflineImageAvailability(
        thumbCached = false,
        previewCached = false,
        resultCached = false,
    )

    override fun offlineCacheStats(): OfflineCacheStats = OfflineCacheStats(bytes = 0L, fileCount = 0)

    override fun clearOfflineImageCache() = Unit
}
