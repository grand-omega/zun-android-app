package dev.zun.flux.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zun.flux.FluxApp
import dev.zun.flux.R
import dev.zun.flux.data.repo.HealthRepository
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.UploadRepository
import dev.zun.flux.util.cacheInputLocally
import dev.zun.flux.util.prepareImageForUpload
import dev.zun.flux.util.sha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    healthRepo: HealthRepository,
    promptRepo: PromptRepository,
    jobRepo: JobRepository,
    uploadRepo: UploadRepository,
    images: ImageSourceRepository,
    repositoryVersion: Long,
    capturedUri: Uri? = null,
    sharedUris: List<Uri> = emptyList(),
    onSharedUrisConsumed: () -> Unit = {},
    onTakePhoto: () -> Unit,
    onGalleryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onJobSubmitted: (String) -> Unit,
    onBatchSubmitted: (List<String>) -> Unit,
    onResumeBatch: (List<String>) -> Unit,
) {
    val viewModel: HomeViewModel =
        viewModel(
            key = "home-$repositoryVersion",
            factory =
            viewModelFactory {
                initializer { HomeViewModel(healthRepo, promptRepo, jobRepo, uploadRepo) }
            },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val tryHarderAvailable by viewModel.tryHarderAvailable.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val activeJobIds by viewModel.activeJobIds.collectAsStateWithLifecycle()
    val priorEditsByUri by viewModel.priorEdits.collectAsStateWithLifecycle()
    val polishState by viewModel.polishState.collectAsStateWithLifecycle()
    val prePolishText by viewModel.prePolishText.collectAsStateWithLifecycle()
    val recentInputIds by remember { images.recentInputIds(3) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var saveDialogLabel by rememberSaveable { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as FluxApp
    val pinnedIds by app.pinnedPrompts.ids.collectAsStateWithLifecycle()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val density = LocalDensity.current
    val refreshThresholdPx = with(density) { 96.dp.toPx() }
    var pullDistancePx by remember { mutableFloatStateOf(0f) }
    val limitImagesMessage = pluralStringResource(R.plurals.home_limit_images_format, MAX_BATCH_IMAGES, MAX_BATCH_IMAGES)
    val cappedImagesMessage = pluralStringResource(R.plurals.home_capped_images_format, MAX_BATCH_IMAGES, MAX_BATCH_IMAGES)
    val promptSavedMessage = stringResource(R.string.home_prompt_saved)
    val couldntLoadImageMessage = stringResource(R.string.home_couldnt_load_image)
    val submittedFailedMessage = (state as? SubmitState.DoneBatch)
        ?.takeIf { it.failed > 0 }
        ?.let { pluralStringResource(R.plurals.home_submitted_failed_format, it.submittedIds.size, it.submittedIds.size, it.failed) }

    // [alreadyProcessed] must be true only for a Uri from images.downloadInputToCache
    // (a "recent photo" re-pick): that's already the exact bytes the server stored from
    // some prior upload, so re-running it through prepareImageForUpload would decode and
    // re-compress it a second time. JPEG re-encoding isn't guaranteed to reproduce the same
    // bytes even from an unmodified decode, which would make its hash below diverge from a
    // fresh pick of the same original photo (or from the very upload it came from) --
    // silently defeating both prior-edit detection and the same-photo-twice check in
    // HomeViewModel.addInputUris. A fresh gallery/camera/share/drag-and-drop pick is
    // unprocessed and must go through prepareImageForUpload to reach that same canonical,
    // hashable representation.
    val appendUris: (List<Uri>, Boolean) -> Unit = { newUris, alreadyProcessed ->
        coroutineScope.launch {
            val remaining = MAX_BATCH_IMAGES - composer.inputUris.size
            if (remaining <= 0) {
                snackbarHostState.showOne(limitImagesMessage)
                return@launch
            }
            val deduped = newUris.filter { it !in composer.inputUris }
            val exactDuplicatesSkipped = newUris.size - deduped.size
            val toAdd = deduped.take(remaining)
            val cappedByCount = deduped.size > toAdd.size
            val cached = withContext(Dispatchers.IO) {
                toAdd.map { uri ->
                    runCatching {
                        if (alreadyProcessed) {
                            cacheInputLocally(context, uri)
                        } else {
                            Uri.fromFile(prepareImageForUpload(context, uri))
                        }
                    }.getOrDefault(uri)
                }
            }
            val hashes = withContext(Dispatchers.IO) {
                cached.mapNotNull { uri ->
                    uri.path?.let { path ->
                        runCatching { sha256Hex(java.io.File(path)) }.getOrNull()?.let { uri to it }
                    }
                }.toMap()
            }
            hashes.forEach { (uri, hash) -> viewModel.checkPriorEdits(uri, hash) }
            val result = viewModel.addInputUris(cached, hashes, MAX_BATCH_IMAGES)
            val duplicatesSkipped = exactDuplicatesSkipped + result.duplicatesSkipped
            // A duplicate photo takes priority over a capacity message — the count you
            // hit is a red herring when the real reason is "that one's already selected".
            when {
                // pluralStringResource can't be called here — the count is only known at
                // runtime inside this callback, not during composition.
                duplicatesSkipped > 0 -> snackbarHostState.showOne(
                    @Suppress("LocalContextResourcesRead")
                    context.resources.getQuantityString(
                        R.plurals.home_duplicate_images_format,
                        duplicatesSkipped,
                        duplicatesSkipped,
                    ),
                )

                cappedByCount || result.capped -> snackbarHostState.showOne(cappedImagesMessage)
            }
        }
    }

    LaunchedEffect(capturedUri) {
        val src = capturedUri ?: return@LaunchedEffect
        appendUris(listOf(src), false)
    }

    // Images arriving via the system share sheet. Consume immediately so a
    // recomposition doesn't re-add them.
    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            appendUris(sharedUris, false)
            onSharedUrisConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.promptSavedEvents.collect {
            snackbarHostState.showOne(promptSavedMessage)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.promptErrors.collect { message ->
            snackbarHostState.showOne(message)
        }
    }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runHealthChecks()
        }
    }

    val pickerSingle =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) appendUris(listOf(uri), false)
        }
    val pickerMulti =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_BATCH_IMAGES),
        ) { uris ->
            if (uris.isNotEmpty()) appendUris(uris, false)
        }
    val launchPicker: () -> Unit = {
        val remaining = MAX_BATCH_IMAGES - composer.inputUris.size
        when {
            remaining <= 0 -> coroutineScope.launch {
                snackbarHostState.showSnackbar(limitImagesMessage)
            }

            remaining == 1 -> pickerSingle.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )

            else -> pickerMulti.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is SubmitState.Done -> {
                viewModel.acknowledgeDone()
                onJobSubmitted(s.jobId)
            }

            is SubmitState.DoneBatch -> {
                val message = submittedFailedMessage
                viewModel.acknowledgeDone()
                onBatchSubmitted(s.submittedIds)
                if (message != null) {
                    coroutineScope.launch { snackbarHostState.showOne(message) }
                }
            }

            else -> Unit
        }
    }

    val isWide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(androidx.window.core.layout.WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_app_title))
                        Spacer(Modifier.width(10.dp))
                        HealthDot(health = health)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = healthShortLabel(health),
                            style = MaterialTheme.typography.labelSmall,
                            color = healthColor(health),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onGalleryClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.home_gallery))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            if (activeJobIds.isNotEmpty()) {
                ActiveJobsBanner(
                    count = activeJobIds.size,
                    onClick = { onResumeBatch(activeJobIds) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.manualRefresh() },
                indicator = {},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(isRefreshing, refreshThresholdPx) {
                        detectVerticalDragGestures(
                            onDragStart = { pullDistancePx = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                if (dragAmount > 0f || pullDistancePx > 0f) {
                                    pullDistancePx = (pullDistancePx + dragAmount)
                                        .coerceIn(0f, refreshThresholdPx * 1.25f)
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (pullDistancePx >= refreshThresholdPx && !isRefreshing) {
                                    viewModel.manualRefresh()
                                }
                                pullDistancePx = 0f
                            },
                            onDragCancel = { pullDistancePx = 0f },
                        )
                    },
            ) {
                val onSubmit: () -> Unit = {
                    if (composer.inputUris.isNotEmpty() && composer.selectedPromptId != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.submit()
                    }
                }
                val onRemoveImage: (Uri) -> Unit = { uri ->
                    viewModel.removeInputUri(uri)
                }
                var isFetchingRecent by remember { mutableStateOf(false) }
                val onPickRecent: (Int) -> Unit = { inputId ->
                    val expectedUri = images.recentInputUri(inputId)
                    if (expectedUri in composer.inputUris) {
                        viewModel.removeInputUri(expectedUri)
                    } else if (!isFetchingRecent) {
                        coroutineScope.launch {
                            isFetchingRecent = true
                            try {
                                val uri = withContext(Dispatchers.IO) {
                                    images.downloadInputToCache(inputId)
                                }
                                viewModel.recordRecentSourceInputId(uri, inputId)
                                appendUris(listOf(uri), true)
                            } catch (_: Throwable) {
                                snackbarHostState.showOne(couldntLoadImageMessage)
                            } finally {
                                isFetchingRecent = false
                            }
                        }
                    }
                }
                val recents = recentInputIds.map { id ->
                    Triple(id, images.inputModel(id), images.recentInputUri(id) in composer.inputUris)
                }

                HomeScreen(
                    isWide = isWide,
                    imageUris = composer.inputUris,
                    prompts = prompts,
                    selectedPromptId = composer.selectedPromptId,
                    customPromptText = composer.customPromptText,
                    onCustomPromptChange = viewModel::updateCustomPrompt,
                    tryHarder = composer.tryHarder,
                    onTryHarderChange = viewModel::setTryHarder,
                    tryHarderAvailable = tryHarderAvailable,
                    onDeletePrompt = viewModel::deletePrompt,
                    onUpdatePrompt = viewModel::updatePrompt,
                    onSavePromptClick = {
                        saveDialogLabel = ""
                        showSaveDialog = true
                    },
                    state = state,
                    uploadProgress = uploadProgress,
                    batchProgress = batchProgress,
                    onTakePhoto = onTakePhoto,
                    onPickGallery = launchPicker,
                    onRemoveImage = onRemoveImage,
                    onSelectPrompt = viewModel::selectPrompt,
                    onSubmit = onSubmit,
                    recents = recents,
                    isFetchingRecent = isFetchingRecent,
                    onPickRecent = onPickRecent,
                    pinnedIds = pinnedIds,
                    onTogglePin = { app.pinnedPrompts.toggle(it) },
                    onImagesDropped = { uris -> appendUris(uris, false) },
                    priorEditsByUri = priorEditsByUri,
                    polishState = polishState,
                    onPolishClick = viewModel::polishPrompt,
                    canRevertPolish = prePolishText != null,
                    onRevertPolishClick = viewModel::revertPolish,
                )
                if (isRefreshing || pullDistancePx > 0f) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.home_save_prompt_title)) },
            text = {
                Column {
                    Text(
                        text = composer.customPromptText.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = saveDialogLabel,
                        onValueChange = { saveDialogLabel = it },
                        label = { Text(stringResource(R.string.home_save_prompt_label)) },
                        placeholder = { Text(stringResource(R.string.home_save_prompt_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = saveDialogLabel.isNotBlank() && composer.customPromptText.isNotBlank(),
                    onClick = {
                        viewModel.savePrompt(saveDialogLabel, composer.customPromptText)
                        showSaveDialog = false
                    },
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Show a snackbar but dismiss any in-flight one first, so rapid taps don't
 * pile up a multi-second queue of identical messages.
 */
private suspend fun SnackbarHostState.showOne(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}
