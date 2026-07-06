package dev.zun.flux.ui.progress

import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.RecordingRepository
import dev.zun.flux.ui.home.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

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

    /** Delegates to RecordingRepository but injects failures and records deleteJob/cancelJob calls. */
    private class FaultyRepository(
        private val getJobError: Throwable? = null,
        private val cancelJobError: Throwable? = null,
        /** When set, getJobFlow reports this as the job's already-synced Room status. */
        private val roomStatus: String? = null,
    ) : JobRepository by RecordingRepository() {
        val deletedJobIds = mutableListOf<String>()
        var cancelJobCalls = 0

        override suspend fun getJob(jobId: String, waitSeconds: Int?): JobStatusDto {
            getJobError?.let { throw it }
            return JobStatusDto(id = jobId, status = roomStatus ?: "running", created_at = 1L)
        }

        override fun getJobFlow(jobId: String) = kotlinx.coroutines.flow.MutableStateFlow(
            roomStatus?.let { JobStatusDto(id = jobId, status = it, created_at = 1L) },
        )

        override suspend fun deleteJob(jobId: String) {
            deletedJobIds.add(jobId)
        }

        override suspend fun cancelJob(jobId: String) {
            cancelJobCalls++
            cancelJobError?.let { throw it }
        }
    }

    @Test
    fun `poll loop cleans up the local record when the server has never heard of the job`() = runTest {
        val repo = FaultyRepository(getJobError = httpError(404))
        val viewModel = ProgressViewModel(repo)

        viewModel.resumePolling("j1")
        advanceTimeBy(1_000)

        assertEquals(listOf("j1"), repo.deletedJobIds)
        val state = viewModel.state.value
        assertTrue(state is PollState.Failed)
        assertTrue("a 404 must be marked confirmedGone", (state as PollState.Failed).confirmedGone)
    }

    @Test
    fun `poll loop does not delete the local record on a transient network error`() = runTest {
        val repo = FaultyRepository(getJobError = IOException("timeout"))
        val viewModel = ProgressViewModel(repo)

        viewModel.resumePolling("j1")
        advanceTimeBy(1_000)

        assertTrue("a transient error must not destroy the local job record", repo.deletedJobIds.isEmpty())
        val state = viewModel.state.value
        assertTrue(state is PollState.Failed)
        assertTrue(
            "a transient error must not be marked confirmedGone — the job could still be running",
            !(state as PollState.Failed).confirmedGone,
        )
    }

    @Test
    fun `dismissing a job the server has never heard of cleans up its local record`() = runTest {
        val repo = FaultyRepository(cancelJobError = httpError(404))
        val viewModel = ProgressViewModel(repo)

        viewModel.cancelJob("j1")
        advanceTimeBy(1_000)

        assertEquals(listOf("j1"), repo.deletedJobIds)
    }

    @Test
    fun `cancelling a still-live job does not touch its local record`() = runTest {
        val repo = FaultyRepository()
        val viewModel = ProgressViewModel(repo)

        viewModel.cancelJob("j1")
        advanceTimeBy(1_000)

        assertEquals(1, repo.cancelJobCalls)
        assertTrue(repo.deletedJobIds.isEmpty())
    }

    @Test
    fun `dismissing an already server-confirmed-failed job skips the network cancel call`() = runTest {
        val repo = FaultyRepository(roomStatus = "failed")
        val viewModel = ProgressViewModel(repo)
        viewModel.resumePolling("j1")
        advanceTimeBy(1_000)
        viewModel.pausePolling()
        assertTrue(viewModel.state.value.let { it is PollState.Failed && it.confirmedGone })

        viewModel.cancelJob("j1")
        advanceTimeBy(1_000)

        assertEquals(
            "no server call is needed — the job is already confirmed gone",
            0,
            repo.cancelJobCalls,
        )
        assertEquals(listOf("j1"), repo.deletedJobIds)
    }

    @Test
    fun `dismissing an already-cancelled job skips the network cancel call`() = runTest {
        val repo = FaultyRepository(roomStatus = "cancelled")
        val viewModel = ProgressViewModel(repo)
        viewModel.resumePolling("j1")
        advanceTimeBy(1_000)
        viewModel.pausePolling()
        assertTrue(viewModel.state.value == PollState.Cancelled)

        viewModel.cancelJob("j1")
        advanceTimeBy(1_000)

        assertEquals(0, repo.cancelJobCalls)
        assertEquals(listOf("j1"), repo.deletedJobIds)
    }

    @Test
    fun `dismissing a transiently-failed job still attempts a real cancel first`() = runTest {
        val repo = FaultyRepository(getJobError = IOException("timeout"))
        val viewModel = ProgressViewModel(repo)
        viewModel.resumePolling("j1")
        advanceTimeBy(1_000)
        viewModel.pausePolling()
        assertTrue(viewModel.state.value.let { it is PollState.Failed && !it.confirmedGone })

        viewModel.cancelJob("j1")
        advanceTimeBy(1_000)

        assertEquals(
            "the job might still be legitimately running — must ask the server to cancel it",
            1,
            repo.cancelJobCalls,
        )
    }

    private fun httpError(code: Int): HttpException = HttpException(
        Response.error<Unit>(
            "".toResponseBody("application/json".toMediaType()),
            okhttp3.Response.Builder()
                .request(Request.Builder().url("http://x/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("err")
                .build(),
        ),
    )
}
