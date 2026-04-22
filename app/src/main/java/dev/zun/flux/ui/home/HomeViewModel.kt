package dev.zun.flux.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.repo.JobRepository
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

class HomeViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val state: StateFlow<SubmitState> = _state.asStateFlow()

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
