package dev.zun.flux.ui.gallery

import dev.zun.flux.data.repo.RecordingRepository
import dev.zun.flux.ui.home.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Covers [GalleryViewModel.revealEligibleJobIds]' active-id diffing (feature 011): FR-001-004.
 * Verifies only the eligibility bookkeeping — not the visual animation, which isn't meaningfully
 * unit-testable (see spec 011's quickstart.md / Constitution Principle III split).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelRevealTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: RecordingRepository
    private lateinit var viewModel: GalleryViewModel

    @Before
    fun setUp() {
        repository = RecordingRepository()
    }

    private fun createViewModel() {
        viewModel = GalleryViewModel(jobRepo = repository, promptRepo = repository, imageRepo = repository)
    }

    @Test
    fun idAbsentFromASubsequentEmissionBecomesEligible() = runTest {
        createViewModel()
        runCurrent() // baseline: empty

        repository.setActiveJobIds(listOf("job-1"))
        runCurrent() // job-1 now active — not a completion, just newly-seen

        repository.setActiveJobIds(emptyList())
        runCurrent() // job-1 disappeared — a real active-to-done transition

        assertEquals(setOf("job-1"), viewModel.revealEligibleJobIds.value)
    }

    @Test
    fun idPresentOnlyInTheFirstEverEmissionIsNotEligibleOnItsOwn() = runTest {
        // job-1 is already active before the ViewModel starts observing at all —
        // simulates a job that was mid-processing when Gallery opened.
        repository.setActiveJobIds(listOf("job-1"))
        createViewModel()
        runCurrent() // first emission ever seen is the baseline, not diffed

        assertTrue(viewModel.revealEligibleJobIds.value.isEmpty())
    }

    @Test
    fun markRevealedConsumesAnIdSoItDoesNotReappear() = runTest {
        createViewModel()
        runCurrent()
        repository.setActiveJobIds(listOf("job-1"))
        runCurrent()
        repository.setActiveJobIds(emptyList())
        runCurrent()
        assertEquals(setOf("job-1"), viewModel.revealEligibleJobIds.value)

        viewModel.markRevealed("job-1")
        assertTrue(viewModel.revealEligibleJobIds.value.isEmpty())

        // A later, unrelated emission must not resurrect it.
        repository.setActiveJobIds(listOf("job-2"))
        runCurrent()
        repository.setActiveJobIds(emptyList())
        runCurrent()
        assertEquals(setOf("job-2"), viewModel.revealEligibleJobIds.value)
    }

    @Test
    fun multipleIdsDisappearingInTheSameEmissionAllBecomeEligibleTogether() = runTest {
        createViewModel()
        runCurrent()
        repository.setActiveJobIds(listOf("job-1", "job-2", "job-3"))
        runCurrent()

        repository.setActiveJobIds(listOf("job-2"))
        runCurrent()

        assertEquals(setOf("job-1", "job-3"), viewModel.revealEligibleJobIds.value)
    }
}
