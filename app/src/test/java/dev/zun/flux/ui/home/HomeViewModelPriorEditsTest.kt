package dev.zun.flux.ui.home

import android.net.Uri
import dev.zun.flux.data.repo.PriorEditsInfo
import dev.zun.flux.data.repo.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers User Story 1's detection state (`HomeViewModel.priorEdits`). Uses
 * Robolectric (unlike the plain-JVM `HomeViewModelTest`) because a real,
 * usable `Uri` instance — needed here as a map key — isn't available from
 * the stub android.jar used by default unit tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelPriorEditsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: RecordingRepository
    private lateinit var viewModel: HomeViewModel
    private val testUri: Uri = Uri.parse("content://test/photo-1")

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
    fun checkPriorEdits_recordsAMatchByUri() = runTest {
        runCurrent()
        repository.priorEditsResult = PriorEditsInfo(lineageRootId = "job-1", editCount = 2)

        viewModel.checkPriorEdits(testUri, "some-sha256")
        advanceUntilIdle()

        assertEquals(
            PriorEditsInfo(lineageRootId = "job-1", editCount = 2),
            viewModel.priorEdits.value[testUri],
        )
    }

    @Test
    fun checkPriorEdits_recordsNothingWhenNoMatch() = runTest {
        runCurrent()
        repository.priorEditsResult = null

        viewModel.checkPriorEdits(testUri, "some-sha256")
        advanceUntilIdle()

        assertTrue(viewModel.priorEdits.value.isEmpty())
    }

    @Test
    fun removeInputUri_alsoClearsItsPriorEditsEntry() = runTest {
        runCurrent()
        repository.priorEditsResult = PriorEditsInfo(lineageRootId = "job-1", editCount = 1)
        viewModel.checkPriorEdits(testUri, "some-sha256")
        advanceUntilIdle()

        viewModel.removeInputUri(testUri)

        assertTrue(viewModel.priorEdits.value.isEmpty())
    }
}
