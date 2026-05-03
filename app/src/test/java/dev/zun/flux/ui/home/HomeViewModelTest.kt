package dev.zun.flux.ui.home

import android.net.Uri
import dev.zun.flux.data.api.HealthResponse
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.JobListResponse
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.ConnectionDiagnosis
import dev.zun.flux.data.repo.JobRepository
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
        viewModel = HomeViewModel(repository)
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

private class RecordingRepository : JobRepository {
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
        promptId: Long?,
        promptText: String?,
        workflow: String?,
        onUploadProgress: ((Float) -> Unit)?,
    ): JobCreatedResponse {
        lastPromptId = promptId
        lastPromptText = promptText
        onUploadProgress?.invoke(1f)
        return JobCreatedResponse(job_id = "job-1", input_id = 1)
    }

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
}
