package dev.zun.flux.ui.home

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobCreatedResponse
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.Workflows
import dev.zun.flux.data.repo.ConnectionDiagnosis
import dev.zun.flux.data.repo.HealthRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.JobUploadStatus
import dev.zun.flux.data.repo.PriorEditsInfo
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.PromptSelection
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
private const val DEFAULT_CUSTOM_WORKFLOW = Workflows.DEFAULT_EDIT
private const val TRY_HARDER_WORKFLOW = Workflows.TRY_HARDER_EDIT

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

sealed interface PolishState {
    data object Idle : PolishState

    data object InProgress : PolishState

    data class Failed(val message: String) : PolishState
}

/** Progress through a batch submit: "Uploading [current] of [total]". */
data class BatchProgress(val current: Int, val total: Int)

data class HomeComposerState(
    val inputUris: List<Uri> = emptyList(),
    val selectedPromptId: Long? = null,
    val customPromptText: String = "",
    val tryHarder: Boolean = false,
)

data class AddInputResult(val capped: Boolean, val duplicatesSkipped: Int = 0)

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

    /**
     * Whether the server supports the Try-harder workflow, per /capabilities.
     * Defaults to false until the catalog loads — hiding a toggle briefly is
     * better than offering one the server will reject.
     */
    private val _tryHarderAvailable = MutableStateFlow(false)
    val tryHarderAvailable: StateFlow<Boolean> = _tryHarderAvailable.asStateFlow()

    private val _composer = MutableStateFlow(HomeComposerState())
    val composer: StateFlow<HomeComposerState> = _composer.asStateFlow()

    private val _polishState = MutableStateFlow<PolishState>(PolishState.Idle)
    val polishState: StateFlow<PolishState> = _polishState.asStateFlow()

    /** Custom prompt text as it was just before the most recent successful polish, for revert. */
    private val _prePolishText = MutableStateFlow<String?>(null)
    val prePolishText: StateFlow<String?> = _prePolishText.asStateFlow()

    private val _priorEdits = MutableStateFlow<Map<Uri, PriorEditsInfo>>(emptyMap())
    val priorEdits: StateFlow<Map<Uri, PriorEditsInfo>> = _priorEdits.asStateFlow()

    /** Which recent inputId (if any) a staged [Uri] was re-downloaded from — see [submitOne]. */
    private val recentSourceInputIds = MutableStateFlow<Map<Uri, Int>>(emptyMap())

    /**
     * Content hash of each selected [Uri], so [addInputUris] can reject a photo that's
     * already selected under a *different* Uri — a fresh gallery re-pick and a "recent"
     * re-download both mint a new Uri for what may be byte-identical content, so exact-Uri
     * equality alone lets the same photo end up selected (and processed) twice.
     */
    private val inputHashes = mutableMapOf<Uri, String>()

    val prompts: StateFlow<List<PromptDto>> = promptRepo.promptsState
        .map { fetched ->
            clearDeletedPromptSelection(fetched)
            fetched + CUSTOM_PROMPT
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = listOf(CUSTOM_PROMPT),
        )

    /** Ids of jobs still processing, so Home can offer a way back into their live view. */
    val activeJobIds: StateFlow<List<String>> = jobRepo.activeJobIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
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
        viewModelScope.launch { fetchCapabilities() }
    }

    private suspend fun fetchPrompts() {
        runCatching { promptRepo.listPrompts() }
            .onFailure { Log.w(TAG, "Prompt fetch failed", it) }
    }

    private suspend fun fetchCapabilities() {
        runCatching { healthRepo.capabilities() }
            .onSuccess { caps ->
                _tryHarderAvailable.value = caps.workflows
                    .any { it.name == Workflows.TRY_HARDER_EDIT && it.supported }
            }
            .onFailure { Log.w(TAG, "Capabilities fetch failed", it) }
    }

    suspend fun runHealthChecks() {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            performHealthCheck()
            failures = if (_health.value == HealthState.Connected) 0 else failures + 1
            delay(healthPollDelayMs(failures))
        }
    }

    /** 30s while connected; back off to 60s then 120s while the server stays unreachable. */
    private fun healthPollDelayMs(failures: Int): Long = when {
        failures <= 1 -> 30_000L
        failures == 2 -> 60_000L
        else -> 120_000L
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

    /**
     * [hashesOf] should carry every entry in [uris] that's known (best-effort —
     * an unhashable candidate is still added, just not content-deduped).
     */
    fun addInputUris(uris: List<Uri>, hashesOf: Map<Uri, String> = emptyMap(), maxImages: Int): AddInputResult {
        val current = _composer.value.inputUris
        val remaining = maxImages - current.size
        if (remaining <= 0) return AddInputResult(capped = true)

        val seenHashes = current.mapNotNullTo(mutableSetOf()) { inputHashes[it] }
        var duplicatesSkipped = 0
        val deduped = uris.filter { uri ->
            val hash = hashesOf[uri]
            val isDuplicate = uri in current || (hash != null && !seenHashes.add(hash))
            if (isDuplicate) duplicatesSkipped++
            !isDuplicate
        }
        val toAdd = deduped.take(remaining)

        toAdd.forEach { uri -> hashesOf[uri]?.let { inputHashes[uri] = it } }
        _composer.value = _composer.value.copy(inputUris = current + toAdd)

        return AddInputResult(capped = deduped.size > toAdd.size, duplicatesSkipped = duplicatesSkipped)
    }

    fun removeInputUri(uri: Uri) {
        _composer.value = _composer.value.copy(inputUris = _composer.value.inputUris - uri)
        _priorEdits.value = _priorEdits.value - uri
        recentSourceInputIds.value = recentSourceInputIds.value - uri
        inputHashes -= uri
    }

    /** Records that [uri] is a re-download of recent photo [inputId], so [submitOne] can tie the
     *  new job's lineage to that original input directly instead of re-deriving it from a hash. */
    fun recordRecentSourceInputId(uri: Uri, inputId: Int) {
        recentSourceInputIds.value = recentSourceInputIds.value + (uri to inputId)
    }

    /** Checks whether [uri]'s content (already hashed as [sha256]) matches a prior completed job. */
    fun checkPriorEdits(uri: Uri, sha256: String) {
        viewModelScope.launch {
            val info = uploadRepo.findPriorEdits(sha256)
            // The user may have removed uri while this suspended — don't resurrect a stale
            // entry for an image that's no longer selected.
            if (info != null && uri in _composer.value.inputUris) {
                _priorEdits.value = _priorEdits.value + (uri to info)
            }
        }
    }

    fun selectPrompt(promptId: Long) {
        _composer.value = _composer.value.copy(selectedPromptId = promptId)
    }

    fun updateCustomPrompt(text: String) {
        _composer.value = _composer.value.copy(customPromptText = text)
        // A manual edit ends the previous polish's revert window (FR-003's "for as long as
        // that prompt-writing session is still active") — the polish success path mutates
        // customPromptText directly, bypassing this, so it doesn't clear its own target.
        _prePolishText.value = null
    }

    /** Rewrites the current custom prompt via [PromptRepository.polishPrompt]. See FR-001-008. */
    fun polishPrompt() {
        val requestText = _composer.value.customPromptText
        if (requestText.isBlank() || _polishState.value is PolishState.InProgress) return
        _polishState.value = PolishState.InProgress
        viewModelScope.launch {
            val outcome = runCatching { promptRepo.polishPrompt(requestText) }
            if (_composer.value.customPromptText != requestText) {
                // Stale: the user changed the text while this was in flight — discard
                // whatever came back rather than clobbering their newer edit (FR-005/FR spec
                // Edge Cases).
                _polishState.value = PolishState.Idle
                return@launch
            }
            _polishState.value = outcome.fold(
                onSuccess = { rewritten ->
                    _prePolishText.value = requestText
                    _composer.value = _composer.value.copy(customPromptText = rewritten)
                    PolishState.Idle
                },
                onFailure = { PolishState.Failed(it.toUserMessage("polish")) },
            )
        }
    }

    /** Restores the custom prompt text to what it was before the last successful polish. */
    fun revertPolish() {
        val original = _prePolishText.value ?: return
        _composer.value = _composer.value.copy(customPromptText = original)
        _prePolishText.value = null
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
        val selection = if (selectedPromptId == CUSTOM_PROMPT_ID) {
            PromptSelection.Custom(customPromptText)
        } else {
            PromptSelection.Saved(selectedPromptId)
        }
        val workId = uploadRepo.enqueueJobUpload(
            inputUri = inputUri,
            selection = selection,
            workflow = workflow,
            knownSourceInputId = recentSourceInputIds.value[inputUri],
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
            _priorEdits.value = emptyMap()
            recentSourceInputIds.value = emptyMap()
            _prePolishText.value = null
            _polishState.value = PolishState.Idle
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
                _promptErrors.trySend(t.toUserMessage("save prompt"))
            }
        }
    }

    fun updatePrompt(promptId: Long, label: String, text: String) {
        viewModelScope.launch {
            try {
                promptRepo.updatePrompt(promptId, label.trim(), text.trim())
            } catch (t: Throwable) {
                _promptErrors.trySend(t.toUserMessage("update prompt"))
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
                _promptErrors.trySend(t.toUserMessage("delete prompt"))
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
