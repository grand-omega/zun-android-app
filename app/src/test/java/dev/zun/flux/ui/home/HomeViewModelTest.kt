package dev.zun.flux.ui.home

import android.net.Uri
import dev.zun.flux.data.repo.RecordingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        assertEquals("flux2_klein_9b_kv_edit", repository.lastWorkflow)
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
