package dev.zun.flux.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.HealthRepository
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.UploadRepository
import dev.zun.flux.ui.common.ControlShape
import dev.zun.flux.util.cacheInputLocally
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    healthRepo: HealthRepository,
    promptRepo: PromptRepository,
    jobRepo: JobRepository,
    uploadRepo: UploadRepository,
    images: ImageSourceRepository,
    repositoryVersion: Long,
    windowSizeClass: WindowSizeClass,
    capturedUri: Uri? = null,
    onTakePhoto: () -> Unit,
    onGalleryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onJobSubmitted: (String) -> Unit,
    onBatchSubmitted: (List<String>) -> Unit,
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
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
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

    val appendUris: (List<Uri>) -> Unit = { newUris ->
        coroutineScope.launch {
            val remaining = MAX_BATCH_IMAGES - composer.inputUris.size
            if (remaining <= 0) {
                snackbarHostState.showOne(context.getString(R.string.home_limit_images_format, MAX_BATCH_IMAGES))
                return@launch
            }
            val toAdd = newUris.filter { it !in composer.inputUris }.take(remaining)
            val cached = withContext(Dispatchers.IO) {
                toAdd.map { uri -> runCatching { cacheInputLocally(context, uri) }.getOrDefault(uri) }
            }
            val result = viewModel.addInputUris(cached, MAX_BATCH_IMAGES)
            if (newUris.size > toAdd.size || result.capped) {
                snackbarHostState.showOne(context.getString(R.string.home_capped_images_format, MAX_BATCH_IMAGES))
            }
        }
    }

    LaunchedEffect(capturedUri) {
        val src = capturedUri ?: return@LaunchedEffect
        appendUris(listOf(src))
    }

    LaunchedEffect(Unit) {
        viewModel.promptSavedEvents.collect {
            snackbarHostState.showOne(context.getString(R.string.home_prompt_saved))
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
            if (uri != null) appendUris(listOf(uri))
        }
    val pickerMulti =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_BATCH_IMAGES),
        ) { uris ->
            if (uris.isNotEmpty()) appendUris(uris)
        }
    val launchPicker: () -> Unit = {
        val remaining = MAX_BATCH_IMAGES - composer.inputUris.size
        when {
            remaining <= 0 -> coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.home_limit_images_format, MAX_BATCH_IMAGES))
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
                viewModel.acknowledgeDone()
                if (s.failed > 0) {
                    snackbarHostState.showOne(
                        context.getString(R.string.home_submitted_failed_format, s.submittedIds.size, s.failed),
                    )
                }
                onBatchSubmitted(s.submittedIds)
            }

            else -> Unit
        }
    }

    val isWide = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

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
                            text = stringResource(
                                R.string.home_status_format,
                                activeRouteLabel(app.settingsManager.activeRoute),
                                healthShortLabel(health),
                            ),
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.manualRefresh() },
            indicator = {},
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
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
                    // Tap a selected recent to toggle it off.
                    viewModel.removeInputUri(expectedUri)
                } else if (!isFetchingRecent) {
                    coroutineScope.launch {
                        isFetchingRecent = true
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                images.downloadInputToCache(inputId)
                            }
                            appendUris(listOf(uri))
                        } catch (_: Throwable) {
                            snackbarHostState.showOne(context.getString(R.string.home_couldnt_load_image))
                        } finally {
                            isFetchingRecent = false
                        }
                    }
                }
            }
            val recents = recentInputIds.map { id ->
                Triple(id, images.inputModel(id), images.recentInputUri(id) in composer.inputUris)
            }

            HomeContent(
                isWide = isWide,
                imageUris = composer.inputUris,
                prompts = prompts,
                selectedPromptId = composer.selectedPromptId,
                customPromptText = composer.customPromptText,
                onCustomPromptChange = viewModel::updateCustomPrompt,
                tryHarder = composer.tryHarder,
                onTryHarderChange = viewModel::setTryHarder,
                onDeletePrompt = viewModel::deletePrompt,
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    isWide: Boolean,
    imageUris: List<Uri>,
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    tryHarder: Boolean,
    onTryHarderChange: (Boolean) -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onSavePromptClick: () -> Unit,
    state: SubmitState,
    uploadProgress: Float?,
    batchProgress: BatchProgress?,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSelectPrompt: (Long) -> Unit,
    onSubmit: () -> Unit,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickRecent: (Int) -> Unit,
    pinnedIds: Set<Long>,
    onTogglePin: (Long) -> Unit,
) {
    var showPromptSheet by rememberSaveable { mutableStateOf(false) }
    var showPromptManageSheet by rememberSaveable { mutableStateOf(false) }

    val canSubmit = imageUris.isNotEmpty() &&
        selectedPromptId != null &&
        (selectedPromptId != CUSTOM_PROMPT_ID || customPromptText.isNotBlank()) &&
        state !is SubmitState.InFlight

    val selectedLabel = remember(prompts, selectedPromptId, customPromptText) {
        when (val id = selectedPromptId) {
            null -> null

            CUSTOM_PROMPT_ID -> customPromptText.trim().takeIf { it.isNotBlank() }?.let {
                if (it.length <= 40) it else it.take(37) + "…"
            }

            else -> prompts.firstOrNull { it.id == id }?.label
        }
    }

    val composer: @Composable () -> Unit = {
        Composer(
            selectedLabel = selectedLabel,
            tryHarder = tryHarder,
            uploadProgress = uploadProgress,
            batchProgress = batchProgress,
            state = state,
            imageCount = imageUris.size,
            canSubmit = canSubmit,
            onPromptStripClick = { showPromptSheet = true },
            onSubmit = onSubmit,
            onTakePhoto = onTakePhoto,
            onPickGallery = onPickGallery,
            recents = recents,
            isFetchingRecent = isFetchingRecent,
            onPickRecent = onPickRecent,
            showSourceRow = imageUris.isNotEmpty(),
        )
    }

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                ImageHero(
                    imageUris = imageUris,
                    recents = recents,
                    isFetchingRecent = isFetchingRecent,
                    onPickGallery = onPickGallery,
                    onTakePhoto = onTakePhoto,
                    onPickRecent = onPickRecent,
                    onRemove = onRemoveImage,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                composer()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ImageHero(
                    imageUris = imageUris,
                    recents = recents,
                    isFetchingRecent = isFetchingRecent,
                    onPickGallery = onPickGallery,
                    onTakePhoto = onTakePhoto,
                    onPickRecent = onPickRecent,
                    onRemove = onRemoveImage,
                )
            }
            composer()
        }
    }

    if (showPromptSheet) {
        PromptLibrarySheet(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            customPromptText = customPromptText,
            onCustomPromptChange = onCustomPromptChange,
            tryHarder = tryHarder,
            onTryHarderChange = onTryHarderChange,
            onSavePromptClick = onSavePromptClick,
            onManagePrompts = {
                showPromptSheet = false
                showPromptManageSheet = true
            },
            onSelectPrompt = { id ->
                onSelectPrompt(id)
                if (id != CUSTOM_PROMPT_ID) showPromptSheet = false
            },
            onDismiss = { showPromptSheet = false },
            pinnedIds = pinnedIds,
            onTogglePin = onTogglePin,
        )
    }

    if (showPromptManageSheet) {
        PromptManageSheet(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            onDeletePrompt = onDeletePrompt,
            onDismiss = { showPromptManageSheet = false },
        )
    }
}

@Composable
private fun Composer(
    selectedLabel: String?,
    tryHarder: Boolean,
    uploadProgress: Float?,
    batchProgress: BatchProgress?,
    state: SubmitState,
    imageCount: Int,
    canSubmit: Boolean,
    onPromptStripClick: () -> Unit,
    onSubmit: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickRecent: (Int) -> Unit,
    showSourceRow: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showSourceRow) {
            CompactSourceRow(
                imageCount = imageCount,
                onTakePhoto = onTakePhoto,
                onPickGallery = onPickGallery,
                recents = recents,
                isFetchingRecent = isFetchingRecent,
                onPickRecent = onPickRecent,
            )
        }

        PromptStrip(
            selectedLabel = selectedLabel,
            tryHarder = tryHarder,
            onClick = onPromptStripClick,
        )

        UploadProgressSection(uploadProgress = uploadProgress, batchProgress = batchProgress)

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state is SubmitState.InFlight -> stringResource(R.string.home_submitting)
                    imageCount > 1 -> stringResource(R.string.home_generate_count_format, imageCount)
                    else -> stringResource(R.string.home_generate)
                },
            )
        }

        if (!canSubmit && state !is SubmitState.InFlight) {
            Text(
                text = when {
                    imageCount == 0 -> stringResource(R.string.home_need_image)
                    selectedLabel == null -> stringResource(R.string.home_need_prompt)
                    else -> stringResource(R.string.home_finish_prompt)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val failure = state as? SubmitState.Failed
        if (failure != null) {
            Text(failure.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PromptStrip(
    selectedLabel: String?,
    tryHarder: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_prompt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = selectedLabel ?: stringResource(R.string.home_choose_a_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedLabel != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tryHarder) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_hq),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.home_open_prompt_picker),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Composable
private fun UploadProgressSection(
    uploadProgress: Float?,
    batchProgress: BatchProgress?,
) {
    if (uploadProgress == null && batchProgress == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = when {
            batchProgress != null && uploadProgress != null -> stringResource(
                R.string.home_uploading_batch_with_pct_format,
                batchProgress.current,
                batchProgress.total,
                (uploadProgress * 100).toInt(),
            )

            batchProgress != null -> stringResource(
                R.string.home_uploading_batch_format,
                batchProgress.current,
                batchProgress.total,
            )

            uploadProgress != null -> stringResource(
                R.string.home_uploading_pct_format,
                (uploadProgress * 100).toInt(),
            )

            else -> ""
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (uploadProgress != null) {
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
