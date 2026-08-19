package dev.zun.flux.ui.home

import dev.zun.flux.data.repo.RecordingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Covers the custom-prompt "polish" action (spec 013): FR-001-008. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPromptPolishTest {
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
    fun polishPrompt_replacesTextAndTracksPrePolishValueForRevert() = runTest {
        runCurrent()
        viewModel.updateCustomPrompt("cat but cooler")

        viewModel.polishPrompt()
        advanceUntilIdle()

        assertEquals("Polished: cat but cooler", viewModel.composer.value.customPromptText)
        assertEquals("cat but cooler", viewModel.prePolishText.value)
        assertEquals(PolishState.Idle, viewModel.polishState.value)
        assertEquals(1, repository.polishCalls)
    }

    @Test
    fun revertPolish_restoresExactPrePolishWording() = runTest {
        runCurrent()
        viewModel.updateCustomPrompt("cat but cooler")
        viewModel.polishPrompt()
        advanceUntilIdle()

        viewModel.revertPolish()

        assertEquals("cat but cooler", viewModel.composer.value.customPromptText)
        assertNull(viewModel.prePolishText.value)
    }

    @Test
    fun polishPrompt_onFailureLeavesOriginalTextUntouched() = runTest {
        runCurrent()
        viewModel.updateCustomPrompt("cat but cooler")
        repository.polishShouldFail = true

        viewModel.polishPrompt()
        advanceUntilIdle()

        assertEquals("cat but cooler", viewModel.composer.value.customPromptText)
        assertNull(viewModel.prePolishText.value)
        assertTrue(viewModel.polishState.value is PolishState.Failed)
    }

    @Test
    fun polishPrompt_discardsAStaleResponseIfTextChangedWhileInFlight() = runTest {
        runCurrent()
        viewModel.updateCustomPrompt("cat but cooler")
        val hold = CompletableDeferred<Unit>()
        repository.holdPolish = hold

        viewModel.polishPrompt()
        runCurrent()
        // User edits the field again before the in-flight polish resolves.
        viewModel.updateCustomPrompt("dog instead")
        hold.complete(Unit)
        advanceUntilIdle()

        assertEquals("dog instead", viewModel.composer.value.customPromptText)
        assertNull(viewModel.prePolishText.value)
        assertEquals(PolishState.Idle, viewModel.polishState.value)
    }

    @Test
    fun polishPrompt_isANoOpWhenTheFieldIsBlank() = runTest {
        runCurrent()
        viewModel.updateCustomPrompt("   ")

        viewModel.polishPrompt()
        advanceUntilIdle()

        assertEquals(0, repository.polishCalls)
        assertEquals(PolishState.Idle, viewModel.polishState.value)
    }
}
