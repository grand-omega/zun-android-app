package dev.zun.flux.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface PollState {
    data object Starting : PollState

    data class Running(val dto: JobStatusDto) : PollState

    data class Done(val dto: JobStatusDto) : PollState

    data class Failed(val message: String) : PollState
}

class ProgressViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PollState>(PollState.Starting)
    val state: StateFlow<PollState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun start(jobId: String) {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch { pollLoop(jobId) }
    }

    fun retry(jobId: String) {
        pollJob?.cancel()
        _state.value = PollState.Starting
        pollJob = viewModelScope.launch { pollLoop(jobId) }
    }

    private suspend fun CoroutineScope.pollLoop(jobId: String) {
        var errorCount = 0
        while (isActive) {
            try {
                val dto = repository.getJob(jobId)
                errorCount = 0
                _state.value =
                    when (dto.status) {
                        "done" -> PollState.Done(dto)
                        "failed" -> PollState.Failed(dto.error ?: "Job failed")
                        else -> PollState.Running(dto)
                    }
                if ((dto.status == "done") || (dto.status == "failed")) return
            } catch (t: Throwable) {
                errorCount++
                if (errorCount > 5) {
                    _state.value = PollState.Failed("Connection lost: ${t.message}")
                    return
                }
            }
            delay(3_000)
        }
    }
}
