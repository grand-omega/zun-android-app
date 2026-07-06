package dev.zun.flux.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlin.random.Random

sealed interface PollState {
    data object Starting : PollState
    data class Running(val dto: JobStatusDto) : PollState
    data class Done(val dto: JobStatusDto) : PollState
    data object Deleted : PollState

    /** [confirmedGone] is true only when the server has definitively told us this job
     * doesn't exist (a 404, or Room already carrying a server-synced "failed" status) —
     * as opposed to merely failing to check status due to a transient network error,
     * where the job could still be legitimately running. */
    data class Failed(val message: String, val confirmedGone: Boolean = false) : PollState
    data object Cancelled : PollState
}

class ProgressViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PollState>(PollState.Starting)
    val state: StateFlow<PollState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var observeDeletes: Job? = null
    private var pollJob: Job? = null
    private var pollRequests = 0

    /**
     * Balanced with [pausePolling]; network polling runs while any STARTED
     * screen needs it. Refcounted because BatchProgressScreen can compose the
     * same ViewModel from both the grid tile and the focused page at once.
     */
    fun resumePolling(jobId: String) {
        pollRequests++
        start(jobId)
    }

    fun pausePolling() {
        pollRequests = (pollRequests - 1).coerceAtLeast(0)
        if (pollRequests == 0) pollJob?.cancel()
    }

    private fun start(jobId: String) {
        if (observeJob?.isActive == true && pollJob?.isActive == true) return

        if (observeJob?.isActive != true) {
            // Observe local DB for UI updates.
            observeJob = viewModelScope.launch {
                repository.getJobFlow(jobId).collect { job ->
                    _state.value = when {
                        job == null && _state.value !is PollState.Starting -> PollState.Deleted
                        job == null -> PollState.Starting
                        job.status == "done" -> PollState.Done(job)
                        job.status == "failed" -> PollState.Failed(job.error ?: "Job failed", confirmedGone = true)
                        job.status == "cancelled" -> PollState.Cancelled
                        else -> PollState.Running(job)
                    }
                }
            }
        }

        if (observeDeletes?.isActive != true) {
            observeDeletes = viewModelScope.launch {
                repository.deletedJobIds().collect { deletedIds ->
                    if (jobId in deletedIds) {
                        _state.value = PollState.Deleted
                        pollJob?.cancel()
                    }
                }
            }
        }

        // Poll only while a STARTED screen holds a resumePolling request. No
        // background worker keeps running after the progress UI stops.
        pollJob = viewModelScope.launch {
            var iteration = 0
            while (true) {
                try {
                    val job = repository.getJob(jobId)
                    if (job.status == "done" || job.status == "failed" || job.status == "cancelled") {
                        return@launch
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    val notFound = t is HttpException && t.code() == 404
                    _state.value = if (_state.value is PollState.Deleted) {
                        PollState.Deleted
                    } else {
                        PollState.Failed(t.toUserMessage("check job status"), confirmedGone = notFound)
                    }
                    if (notFound) {
                        // The server has no record of this job at all — polling it
                        // again will never succeed. Without this, the stale local
                        // row keeps showing up as "in progress" (it's excluded from
                        // getActiveJobs() only once its status leaves queued/running)
                        // every time the app is reopened.
                        deleteLocalRecordQuietly(jobId)
                    }
                    return@launch
                }
                delay(nextPollDelayMs(iteration++))
            }
        }
    }

    /**
     * Backoff schedule: 5s for the first 3 polls, 10s for the next 2, then 20s,
     * with ±25% jitter to spread server load when many clients poll the same job.
     */
    private fun nextPollDelayMs(iteration: Int): Long {
        val base = when {
            iteration < 3 -> 5_000L
            iteration < 5 -> 10_000L
            else -> 20_000L
        }
        val jitter = (base * 0.25 * (Random.nextDouble() * 2.0 - 1.0)).toLong()
        return base + jitter
    }

    fun retry(jobId: String) {
        pollJob?.cancel()
        pollJob = null
        start(jobId)
    }

    /**
     * Cancels [jobId] server-side, or — when called to dismiss a job already confirmed
     * gone ([PollState.Failed] with `confirmedGone = true`, or [PollState.Cancelled]) —
     * just cleans up its local record without asking the server to cancel it. A merely
     * unreachable-during-polling [PollState.Failed] still goes through a real cancel
     * first, since that job could still be legitimately running.
     */
    fun cancelJob(jobId: String) {
        pollJob?.cancel()
        val alreadyGone = when (val s = _state.value) {
            is PollState.Failed -> s.confirmedGone
            PollState.Cancelled -> true
            else -> false
        }
        viewModelScope.launch {
            if (alreadyGone) {
                deleteLocalRecordQuietly(jobId)
                return@launch
            }
            try {
                repository.cancelJob(jobId)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (t is HttpException && t.code() == 404) {
                    // The server has no record of this job — nothing to cancel, but
                    // the stale local row must go or it'll keep showing as "in progress".
                    deleteLocalRecordQuietly(jobId)
                }
            }
        }
    }

    private suspend fun deleteLocalRecordQuietly(jobId: String) {
        try {
            repository.deleteJob(jobId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Best-effort local cleanup; nothing actionable if it fails.
        }
    }
}
