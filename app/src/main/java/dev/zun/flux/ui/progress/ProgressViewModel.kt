package dev.zun.flux.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface PollState {
    data object Starting : PollState
    data class Running(val dto: JobStatusDto) : PollState
    data class Done(val dto: JobStatusDto) : PollState
    data object Deleted : PollState
    data class Failed(val message: String) : PollState
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
                        job.status == "failed" -> PollState.Failed(job.error ?: "Job failed")
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
                } catch (t: Throwable) {
                    _state.value = if (_state.value is PollState.Deleted) {
                        PollState.Deleted
                    } else {
                        PollState.Failed(t.toUserMessage("check job status"))
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

    fun cancelJob(jobId: String) {
        pollJob?.cancel()
        viewModelScope.launch {
            try {
                repository.cancelJob(jobId)
            } catch (_: Throwable) {
                // 404 means the job already finished — fine to ignore.
            }
        }
    }
}
