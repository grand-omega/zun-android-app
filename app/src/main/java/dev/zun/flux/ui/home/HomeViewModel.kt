package dev.zun.flux.ui.home

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.ConnectionDiagnosis
import dev.zun.flux.data.repo.HealthRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.JobUploadStatus
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.UploadRepository
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Sentinel for the synthetic "Write your own…" tile. Never sent to the server. */
const val CUSTOM_PROMPT_ID: Long = -1L

/** Default workflow for free-text submissions when the user writes a custom prompt. */
private const val DEFAULT_CUSTOM_WORKFLOW = "flux2_klein_edit"
private const val TRY_HARDER_WORKFLOW = "flux2_klein_9b_kv_experimental"

/**
 * Cap on how long [HomeViewModel.submitOne] will wait for a WorkManager
 * upload to reach a terminal state. Without this the wait is unbounded if the
 * worker stays ENQUEUED (e.g. offline → unmet network constraint).
 */
private const val UPLOAD_WAIT_TIMEOUT_MS: Long = 60_000L

sealed interface SubmitState {
    data object Idle : SubmitState

    data object InFlight : SubmitState

    data class Done(val jobId: String) : SubmitState

    data class DoneBatch(val submittedIds: List<String>, val failed: Int) : SubmitState

    data class Failed(val message: String) : SubmitState
}

/** Progress through a batch submit: "Uploading [current] of [total]". */
data class BatchProgress(val current: Int, val total: Int)

data class HomeComposerState(
    val inputUris: List<Uri> = emptyList(),
    val selectedPromptId: Long? = null,
    val customPromptText: String = "",
    val tryHarder: Boolean = false,
)

data class AddInputResult(val capped: Boolean)

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
    private val healthRepo: HealthRepository,
    private val promptRepo: PromptRepository,
    private val jobRepo: JobRepository,
    private val uploadRepo: UploadRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val state: StateFlow<SubmitState> = _state.asStateFlow()

    private val _composer = MutableStateFlow(HomeComposerState())
    val composer: StateFlow<HomeComposerState> = _composer.asStateFlow()

    val prompts: StateFlow<List<PromptDto>> = promptRepo.promptsState
        .map { fetched ->
            clearDeletedPromptSelection(fetched)
            fetched + CUSTOM_PROMPT
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = listOf(CUSTOM_PROMPT),
        )

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
    }

    private suspend fun fetchPrompts() {
        runCatching { promptRepo.listPrompts() }
    }

    suspend fun runHealthChecks() {
        while (currentCoroutineContext().isActive) {
            performHealthCheck()
            delay(30_000)
        }
    }

    private suspend fun performHealthCheck() {
        _health.value =
            try {
                healthRepo.health()
                HealthState.Connected
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    HealthState.Unauthorized
                } else {
                    HealthState.ServerError(e.code(), e.message())
                }
            } catch (_: java.io.IOException) {
                healthRepo.diagnoseConnection().toHealthState()
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
                jobRepo.syncHistory()
                fetchPrompts()
                performHealthCheck()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addInputUris(uris: List<Uri>, maxImages: Int): AddInputResult {
        val current = _composer.value.inputUris
        val remaining = maxImages - current.size
        if (remaining <= 0) return AddInputResult(capped = true)

        val toAdd = uris.filter { it !in current }.take(remaining)
        _composer.value = _composer.value.copy(inputUris = current + toAdd)

        return AddInputResult(capped = uris.size > toAdd.size)
    }

    fun removeInputUri(uri: Uri) {
        _composer.value = _composer.value.copy(inputUris = _composer.value.inputUris - uri)
    }

    fun selectPrompt(promptId: Long) {
        _composer.value = _composer.value.copy(selectedPromptId = promptId)
    }

    fun updateCustomPrompt(text: String) {
        _composer.value = _composer.value.copy(customPromptText = text)
    }

    fun setTryHarder(enabled: Boolean) {
        _composer.value = _composer.value.copy(tryHarder = enabled)
    }

    fun submit() {
        if (_state.value is SubmitState.InFlight) return
        val composer = _composer.value
        val inputUris = composer.inputUris
        val selectedPromptId = composer.selectedPromptId ?: return
        val customPromptText = composer.customPromptText
        val tryHarder = composer.tryHarder

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
                    SubmitState.Failed(t.toUserMessage("submit"))
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
                } catch (t: Throwable) {
                    Log.w(TAG, "Batch submit failed for image ${index + 1}/${inputUris.size}", t)
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
        val promptId = selectedPromptId.takeUnless { it == CUSTOM_PROMPT_ID }
        val promptText = customPromptText.takeIf { selectedPromptId == CUSTOM_PROMPT_ID }
        val workId = uploadRepo.enqueueJobUpload(
            inputUri = inputUri,
            promptId = promptId,
            promptText = promptText,
            workflow = workflow,
        )
        val terminal = try {
            withTimeout(UPLOAD_WAIT_TIMEOUT_MS) {
                uploadRepo.observeJobUpload(workId)
                    .onEach { status ->
                        if (status is JobUploadStatus.InProgress) {
                            _uploadProgress.value = status.progress
                        }
                    }
                    .first { it is JobUploadStatus.Succeeded || it is JobUploadStatus.Failed }
            }
        } catch (_: TimeoutCancellationException) {
            uploadRepo.cancelJobUpload(workId)
            error("Upload timed out — check your connection")
        }
        return when (terminal) {
            is JobUploadStatus.Succeeded -> JobCreatedResponse(
                job_id = terminal.jobId,
                input_id = terminal.inputId,
            )

            is JobUploadStatus.Failed -> error(terminal.message)

            else -> error("Unexpected upload status: $terminal")
        }
    }

    fun acknowledgeDone() {
        if (_state.value is SubmitState.Done || _state.value is SubmitState.DoneBatch) {
            _composer.value = _composer.value.copy(inputUris = emptyList())
        }
        _state.value = SubmitState.Idle
    }

    fun savePrompt(label: String, text: String) {
        viewModelScope.launch {
            try {
                val created = promptRepo.createPrompt(
                    label = label.trim(),
                    text = text.trim(),
                    workflow = DEFAULT_CUSTOM_WORKFLOW,
                )
                _composer.value = _composer.value.copy(
                    selectedPromptId = created.id,
                    customPromptText = "",
                )
                _promptSavedEvents.trySend(created.id)
            } catch (t: Throwable) {
                _promptErrors.trySend(t.message ?: "Failed to save prompt")
            }
        }
    }

    fun deletePrompt(promptId: Long) {
        viewModelScope.launch {
            try {
                promptRepo.deletePrompt(promptId)
                if (_composer.value.selectedPromptId == promptId) {
                    _composer.value = _composer.value.copy(selectedPromptId = null)
                }
            } catch (t: Throwable) {
                _promptErrors.trySend(t.message ?: "Failed to delete prompt")
            }
        }
    }

    private fun clearDeletedPromptSelection(fetchedPrompts: List<PromptDto>) {
        val selectedPromptId = _composer.value.selectedPromptId
        if (selectedPromptId != null && selectedPromptId != CUSTOM_PROMPT_ID && fetchedPrompts.none { it.id == selectedPromptId }) {
            _composer.value = _composer.value.copy(selectedPromptId = null)
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        val CUSTOM_PROMPT = PromptDto(
            id = CUSTOM_PROMPT_ID,
            label = "Write your own...",
            description = "Enter a custom text prompt",
        )
    }
}
