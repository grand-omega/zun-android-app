package dev.zun.flux.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SubmitState {
    data object Idle : SubmitState

    data object InFlight : SubmitState

    data class Done(val jobId: String) : SubmitState

    data class Failed(val message: String) : SubmitState
}

sealed interface HealthState {
    data object Checking : HealthState

    data object Connected : HealthState

    data object Disconnected : HealthState
}

class HomeViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val state: StateFlow<SubmitState> = _state.asStateFlow()

    private val _prompts = MutableStateFlow<List<PromptDto>>(emptyList())
    val prompts: StateFlow<List<PromptDto>> = _prompts.asStateFlow()

    private val _health = MutableStateFlow<HealthState>(HealthState.Checking)
    val health: StateFlow<HealthState> = _health.asStateFlow()

    init {
        fetchPrompts()
        startHealthCheck()
    }

    private fun fetchPrompts() {
        viewModelScope.launch {
            _prompts.value =
                try {
                    repository.listPrompts()
                } catch (_: Throwable) {
                    emptyList()
                }
        }
    }

    private fun startHealthCheck() {
        viewModelScope.launch {
            while (true) {
                _health.value =
                    try {
                        repository.health()
                        HealthState.Connected
                    } catch (_: Throwable) {
                        HealthState.Disconnected
                    }
                delay(10_000) // Check every 10s
            }
        }
    }

    fun submit(
        inputUri: Uri,
        promptId: String,
    ) {
        if (_state.value is SubmitState.InFlight) return
        _state.value = SubmitState.InFlight
        viewModelScope.launch {
            _state.value =
                try {
                    val resp = repository.submitJob(inputUri, promptId)
                    SubmitState.Done(resp.job_id)
                } catch (t: Throwable) {
                    SubmitState.Failed(t.message ?: t::class.simpleName.orEmpty())
                }
        }
    }

    fun acknowledgeDone() {
        _state.value = SubmitState.Idle
    }
}
