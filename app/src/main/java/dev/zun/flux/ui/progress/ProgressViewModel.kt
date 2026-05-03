package dev.zun.flux.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PollState {
    data object Starting : PollState
    data class Running(val dto: JobStatusDto) : PollState
    data class Done(val dto: JobStatusDto) : PollState
    data class Failed(val message: String) : PollState
    data object Cancelled : PollState
}

class ProgressViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PollState>(PollState.Starting)
    val state: StateFlow<PollState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var pollJob: Job? = null

    fun start(jobId: String) {
        if (observeJob?.isActive == true && pollJob?.isActive == true) return

        if (observeJob?.isActive != true) {
            // Observe local DB for UI updates.
            observeJob = viewModelScope.launch {
                repository.getJobFlow(jobId).collect { job ->
                    if (job != null) {
                        _state.value = when (job.status) {
                            "done" -> PollState.Done(job)
                            "failed" -> PollState.Failed(job.error ?: "Job failed")
                            "cancelled" -> PollState.Cancelled
                            else -> PollState.Running(job)
                        }
                    }
                }
            }
        }

        // Poll only while this ViewModel is alive. No background worker is kept
        // running after the progress UI leaves composition.
        pollJob = viewModelScope.launch {
            while (true) {
                try {
                    val job = repository.getJob(jobId)
                    if (job.status == "done" || job.status == "failed" || job.status == "cancelled") {
                        return@launch
                    }
                } catch (t: Throwable) {
                    _state.value = PollState.Failed(t.message ?: "Failed to check job status")
                    return@launch
                }
                delay(5000)
            }
        }
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
