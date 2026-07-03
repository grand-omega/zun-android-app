package dev.zun.flux.ui.progress

import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.RecordingRepository
import dev.zun.flux.ui.home.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Covers the refcounted resume/pause polling contract: network polls run only
 * while at least one STARTED screen holds a request, and overlapping callers
 * (batch tile + focused page) don't cancel each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Delegates everything to RecordingRepository but serves a controllable status and counts polls. */
    private class CountingRepository(
        @Volatile var status: String = "running",
    ) : JobRepository by RecordingRepository() {
        @Volatile var getJobCalls = 0

        override suspend fun getJob(jobId: String, waitSeconds: Int?): JobStatusDto {
            getJobCalls++
            return JobStatusDto(id = jobId, status = status, created_at = 1L)
        }
    }

    @Test
    fun `polls repeatedly while resumed`() = runTest {
        val repo = CountingRepository()
        val viewModel = ProgressViewModel(repo)

        viewModel.resumePolling("j1")
        advanceTimeBy(31_000)

        // 5s cadence for the first polls: expect several calls in 31s.
        assertTrue("expected repeated polls, got ${repo.getJobCalls}", repo.getJobCalls >= 4)

        // Release the request so the poll loop doesn't outlive the test —
        // a live delay loop keeps the virtual-time scheduler from idling.
        viewModel.pausePolling()
    }

    @Test
    fun `pausing the last request stops polling`() = runTest {
        val repo = CountingRepository()
        val viewModel = ProgressViewModel(repo)

        viewModel.resumePolling("j1")
        advanceTimeBy(1_000)
        viewModel.pausePolling()
        val callsAtPause = repo.getJobCalls

        advanceTimeBy(120_000)
        assertEquals(callsAtPause, repo.getJobCalls)
    }

    @Test
    fun `overlapping requests keep polling until the last one pauses`() = runTest {
        val repo = CountingRepository()
        val viewModel = ProgressViewModel(repo)

        viewModel.resumePolling("j1")
        viewModel.resumePolling("j1")
        advanceTimeBy(1_000)
        viewModel.pausePolling()
        val callsAtFirstPause = repo.getJobCalls

        advanceTimeBy(30_000)
        assertTrue(
            "second requester should keep polling",
            repo.getJobCalls > callsAtFirstPause,
        )

        viewModel.pausePolling()
        val callsAtLastPause = repo.getJobCalls
        advanceTimeBy(120_000)
        assertEquals(callsAtLastPause, repo.getJobCalls)
    }

    @Test
    fun `terminal status ends the poll loop`() = runTest {
        val repo = CountingRepository(status = "done")
        val viewModel = ProgressViewModel(repo)

        viewModel.resumePolling("j1")
        advanceTimeBy(120_000)

        assertEquals(1, repo.getJobCalls)
    }
}
