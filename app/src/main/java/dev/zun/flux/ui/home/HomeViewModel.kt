package dev.zun.flux.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.ConnectionDiagnosis
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Sentinel for the synthetic "Write your own…" tile. Never sent to the server. */
const val CUSTOM_PROMPT_ID: Long = -1L

/** Default workflow for free-text submissions when the user writes a custom prompt. */
private const val DEFAULT_CUSTOM_WORKFLOW = "flux2_klein_edit"
private const val TRY_HARDER_WORKFLOW = "flux2_klein_9b_kv_experimental"

sealed interface SubmitState {
    data object Idle : SubmitState

    data object InFlight : SubmitState

    data class Done(val jobId: String) : SubmitState

    data class DoneBatch(val submittedIds: List<String>, val failed: Int) : SubmitState

    data class Failed(val message: String) : SubmitState
}

/** Progress through a batch submit: "Uploading [current] of [total]". */
data class BatchProgress(val current: Int, val total: Int)

sealed interface HealthState {
    data object Checking : HealthState

    data object Connected : HealthState

    data class ServiceDown(val message: String) : HealthState

    data class HostUnreachable(val message: String) : HealthState

    data class NetworkError(val message: String) : HealthState

    data class ServerError(val code: Int, val message: String) : HealthState

    data object Unauthorized : HealthState
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

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _batchProgress = MutableStateFlow<BatchProgress?>(null)
    val batchProgress: StateFlow<BatchProgress?> = _batchProgress.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _promptSavedEvents = Channel<Long>(Channel.BUFFERED)
    val promptSavedEvents: Flow<Long> = _promptSavedEvents.receiveAsFlow()

    private val _promptErrors = Channel<String>(Channel.BUFFERED)
    val promptErrors: Flow<String> = _promptErrors.receiveAsFlow()

    init {
        viewModelScope.launch { fetchPrompts() }
        startHealthCheck()
    }

    private suspend fun fetchPrompts() {
        val fetched =
            try {
                repository.listPrompts()
            } catch (_: Throwable) {
                emptyList()
            }

        val customEntry = PromptDto(
            id = CUSTOM_PROMPT_ID,
            label = "Write your own...",
            description = "Enter a custom text prompt",
        )
        _prompts.value = fetched + customEntry
    }

    private fun startHealthCheck() {
        viewModelScope.launch {
            while (true) {
                performHealthCheck()
                delay(10_000)
            }
        }
    }

    private suspend fun performHealthCheck() {
        _health.value =
            try {
                repository.health()
                HealthState.Connected
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    HealthState.Unauthorized
                } else {
                    HealthState.ServerError(e.code(), e.message())
                }
            } catch (_: java.io.IOException) {
                repository.diagnoseConnection().toHealthState()
            } catch (e: Throwable) {
                HealthState.NetworkError(e.message ?: "Unknown error")
            }
    }

    private fun ConnectionDiagnosis.toHealthState(): HealthState = when (this) {
        ConnectionDiagnosis.Reachable -> HealthState.ServiceDown(
            "Server port is reachable, but /health did not respond. The service may be starting or unhealthy.",
        )
        ConnectionDiagnosis.NoServerUrl -> HealthState.NetworkError("No active server URL")
        is ConnectionDiagnosis.InvalidUrl -> HealthState.NetworkError(message)
        is ConnectionDiagnosis.ServiceNotListening -> HealthState.ServiceDown(message)
        is ConnectionDiagnosis.HostUnreachable -> HealthState.HostUnreachable(message)
        is ConnectionDiagnosis.Unknown -> HealthState.NetworkError(message)
    }

    fun manualRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.syncHistory()
                fetchPrompts()
                performHealthCheck()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun submit(
        inputUris: List<Uri>,
        selectedPromptId: Long,
        customPromptText: String,
        tryHarder: Boolean,
    ) {
        if (_state.value is SubmitState.InFlight) return
        if (inputUris.isEmpty()) return

        if (inputUris.size == 1) {
            submitSingle(inputUris[0], selectedPromptId, customPromptText, tryHarder)
        } else {
            submitBatch(inputUris, selectedPromptId, customPromptText, tryHarder)
        }
    }

    private fun submitSingle(
        inputUri: Uri,
        selectedPromptId: Long,
        customPromptText: String,
        tryHarder: Boolean,
    ) {
        _state.value = SubmitState.InFlight
        _uploadProgress.value = 0f
        viewModelScope.launch {
            _state.value =
                try {
                    val resp = submitOne(inputUri, selectedPromptId, customPromptText, tryHarder)
                    SubmitState.Done(resp.job_id)
                } catch (t: Throwable) {
                    SubmitState.Failed(t.message ?: t::class.simpleName.orEmpty())
                } finally {
                    _uploadProgress.value = null
                }
        }
    }

    private fun submitBatch(
        inputUris: List<Uri>,
        selectedPromptId: Long,
        customPromptText: String,
        tryHarder: Boolean,
    ) {
        _state.value = SubmitState.InFlight
        _uploadProgress.value = 0f
        _batchProgress.value = BatchProgress(current = 1, total = inputUris.size)
        viewModelScope.launch {
            val submittedIds = mutableListOf<String>()
            var failed = 0
            inputUris.forEachIndexed { index, uri ->
                _batchProgress.value = BatchProgress(current = index + 1, total = inputUris.size)
                _uploadProgress.value = 0f
                try {
                    val resp = submitOne(uri, selectedPromptId, customPromptText, tryHarder)
                    submittedIds += resp.job_id
                } catch (_: Throwable) {
                    failed++
                }
            }
            _uploadProgress.value = null
            _batchProgress.value = null
            _state.value = if (submittedIds.isEmpty()) {
                SubmitState.Failed("All $failed uploads failed")
            } else {
                SubmitState.DoneBatch(submittedIds = submittedIds, failed = failed)
            }
        }
    }

    private suspend fun submitOne(
        inputUri: Uri,
        selectedPromptId: Long,
        customPromptText: String,
        tryHarder: Boolean,
    ): JobCreatedResponse {
        val workflow = if (tryHarder) TRY_HARDER_WORKFLOW else DEFAULT_CUSTOM_WORKFLOW
        return if (selectedPromptId == CUSTOM_PROMPT_ID) {
            repository.submitJob(
                inputUri = inputUri,
                promptText = customPromptText,
                workflow = workflow,
                onUploadProgress = { progress -> _uploadProgress.value = progress },
            )
        } else {
            repository.submitJob(
                inputUri = inputUri,
                promptId = selectedPromptId,
                workflow = workflow,
                onUploadProgress = { progress -> _uploadProgress.value = progress },
            )
        }
    }

    fun acknowledgeDone() {
        _state.value = SubmitState.Idle
    }

    fun savePrompt(label: String, text: String) {
        viewModelScope.launch {
            try {
                val created = repository.createPrompt(
                    label = label.trim(),
                    text = text.trim(),
                    workflow = DEFAULT_CUSTOM_WORKFLOW,
                )
                fetchPrompts()
                _promptSavedEvents.trySend(created.id)
            } catch (t: Throwable) {
                _promptErrors.trySend(t.message ?: "Failed to save prompt")
            }
        }
    }

    fun deletePrompt(promptId: Long) {
        viewModelScope.launch {
            try {
                repository.deletePrompt(promptId)
                fetchPrompts()
            } catch (t: Throwable) {
                _promptErrors.trySend(t.message ?: "Failed to delete prompt")
            }
        }
    }
}
