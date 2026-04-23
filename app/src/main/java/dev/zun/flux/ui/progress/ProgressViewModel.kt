package dev.zun.flux.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.worker.PollWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PollState {
    data object Starting : PollState
    data class Running(val dto: JobStatusDto) : PollState
    data class Done(val dto: JobStatusDto) : PollState
    data class Failed(val message: String) : PollState
}

class ProgressViewModel(
    private val repository: JobRepository,
    private val workManager: WorkManager,
) : ViewModel() {
    private val _state = MutableStateFlow<PollState>(PollState.Starting)
    val state: StateFlow<PollState> = _state.asStateFlow()

    private var observeJob: Job? = null

    fun start(jobId: String) {
        if (observeJob?.isActive == true) return

        // 1. Enqueue background polling
        val workRequest = OneTimeWorkRequestBuilder<PollWorker>()
            .setInputData(Data.Builder().putString(PollWorker.KEY_JOB_ID, jobId).build())
            .addTag(jobId)
            .build()
        workManager.enqueue(workRequest)

        // 2. Observe local DB for UI updates
        observeJob = viewModelScope.launch {
            repository.getJobFlow(jobId).collect { job ->
                if (job != null) {
                    _state.value = when (job.status) {
                        "done" -> PollState.Done(job)
                        "failed" -> PollState.Failed(job.error ?: "Job failed")
                        else -> PollState.Running(job)
                    }
                }
            }
        }
    }

    fun retry(jobId: String) {
        start(jobId) // start already enqueues worker and observes
    }

    fun cancelJob(jobId: String) {
        workManager.cancelAllWorkByTag(jobId) // We didn't tag it yet, but can add it
        viewModelScope.launch {
            try {
                repository.deleteJob(jobId)
            } catch (_: Throwable) {
            }
        }
    }
}
