package dev.zun.flux.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.util.cacheInputLocally
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: JobRepository,
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
            factory =
            viewModelFactory {
                initializer { HomeViewModel(repository) }
            },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val recentInputIds by remember { repository.recentInputIds(3) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var imageUris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedPromptId by rememberSaveable { mutableStateOf<Long?>(null) }
    var customPromptText by rememberSaveable { mutableStateOf("") }
    var deleteMode by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var saveDialogLabel by rememberSaveable { mutableStateOf("") }
    var tryHarder by rememberSaveable { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val appendUris: (List<Uri>) -> Unit = { newUris ->
        coroutineScope.launch {
            val remaining = MAX_BATCH_IMAGES - imageUris.size
            if (remaining <= 0) {
                snackbarHostState.showOne("Limit is $MAX_BATCH_IMAGES images")
                return@launch
            }
            val toAdd = newUris.filter { it !in imageUris }.take(remaining)
            val cached = withContext(Dispatchers.IO) {
                toAdd.map { uri -> runCatching { cacheInputLocally(context, uri) }.getOrDefault(uri) }
            }
            imageUris = imageUris + cached
            if (newUris.size > toAdd.size) {
                snackbarHostState.showOne("Capped at $MAX_BATCH_IMAGES images")
            }
        }
    }

    LaunchedEffect(capturedUri) {
        val src = capturedUri ?: return@LaunchedEffect
        appendUris(listOf(src))
    }

    LaunchedEffect(Unit) {
        viewModel.promptSavedEvents.collect { newId ->
            selectedPromptId = newId
            customPromptText = ""
            snackbarHostState.showOne("Prompt saved")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.promptErrors.collect { message ->
            snackbarHostState.showOne(message)
        }
    }

    // If the selected prompt gets deleted, clear the selection.
    LaunchedEffect(prompts, selectedPromptId) {
        val current = selectedPromptId
        if (current != null && current != CUSTOM_PROMPT_ID && prompts.none { it.id == current }) {
            selectedPromptId = null
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
        val remaining = MAX_BATCH_IMAGES - imageUris.size
        when {
            remaining <= 0 -> coroutineScope.launch {
                snackbarHostState.showSnackbar("Limit is $MAX_BATCH_IMAGES images")
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
                imageUris = emptyList()
                onJobSubmitted(s.jobId)
            }
            is SubmitState.DoneBatch -> {
                viewModel.acknowledgeDone()
                imageUris = emptyList()
                if (s.failed > 0) {
                    snackbarHostState.showOne(
                        "${s.submittedIds.size} submitted, ${s.failed} failed",
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
                        Text("FluxEdit")
                        Spacer(Modifier.width(10.dp))
                        HealthDot(health = health)
                    }
                },
                actions = {
                    IconButton(onClick = onGalleryClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Gallery")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        val exitModifier = if (deleteMode) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    deleteMode = false
                }
            }
        } else {
            Modifier
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.manualRefresh() },
            modifier = Modifier
                .padding(inner)
                .then(exitModifier),
        ) {
            val onSubmit: () -> Unit = {
                val promptId = selectedPromptId
                if (imageUris.isNotEmpty() && promptId != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.submit(imageUris, promptId, customPromptText, tryHarder)
                }
            }
            val onRemoveImage: (Uri) -> Unit = { uri ->
                imageUris = imageUris - uri
            }
            var isFetchingRecent by remember { mutableStateOf(false) }
            val onPickRecent: (Int) -> Unit = { inputId ->
                val expectedUri = repository.recentInputUri(inputId)
                if (expectedUri in imageUris) {
                    // Tap a selected recent to toggle it off.
                    imageUris = imageUris - expectedUri
                } else if (!isFetchingRecent) {
                    coroutineScope.launch {
                        isFetchingRecent = true
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                repository.downloadInputToCache(inputId)
                            }
                            appendUris(listOf(uri))
                        } catch (_: Throwable) {
                            snackbarHostState.showOne("Couldn't load that image")
                        } finally {
                            isFetchingRecent = false
                        }
                    }
                }
            }
            val recents = recentInputIds.map { id ->
                Triple(id, repository.inputModel(id), repository.recentInputUri(id) in imageUris)
            }

            HomeContent(
                isWide = isWide,
                imageUris = imageUris,
                prompts = prompts,
                selectedPromptId = selectedPromptId,
                customPromptText = customPromptText,
                onCustomPromptChange = { customPromptText = it },
                tryHarder = tryHarder,
                onTryHarderChange = { tryHarder = it },
                deleteMode = deleteMode,
                onLongPressChip = { deleteMode = true },
                onExitDeleteMode = { deleteMode = false },
                onDeletePrompt = { id ->
                    viewModel.deletePrompt(id)
                    if (selectedPromptId == id) selectedPromptId = null
                },
                onSavePromptClick = {
                    saveDialogLabel = ""
                    showSaveDialog = true
                },
                state = state,
                health = health,
                uploadProgress = uploadProgress,
                batchProgress = batchProgress,
                onTakePhoto = onTakePhoto,
                onPickGallery = launchPicker,
                onRemoveImage = onRemoveImage,
                onSelectPrompt = { selectedPromptId = it },
                onSubmit = onSubmit,
                recents = recents,
                isFetchingRecent = isFetchingRecent,
                onPickRecent = onPickRecent,
            )
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save this prompt") },
            text = {
                Column {
                    Text(
                        text = customPromptText.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = saveDialogLabel,
                        onValueChange = { saveDialogLabel = it },
                        label = { Text("Label") },
                        placeholder = { Text("e.g. Van Gogh style") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = saveDialogLabel.isNotBlank() && customPromptText.isNotBlank(),
                    onClick = {
                        viewModel.savePrompt(saveDialogLabel, customPromptText)
                        showSaveDialog = false
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
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
    deleteMode: Boolean,
    onLongPressChip: () -> Unit,
    onExitDeleteMode: () -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onSavePromptClick: () -> Unit,
    state: SubmitState,
    health: HealthState,
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
) {
    var showPromptSheet by rememberSaveable { mutableStateOf(false) }

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
            deleteMode = deleteMode,
            onLongPressChip = onLongPressChip,
            onExitDeleteMode = onExitDeleteMode,
            onDeletePrompt = onDeletePrompt,
            onSavePromptClick = onSavePromptClick,
            onSelectPrompt = { id ->
                onSelectPrompt(id)
                if (id != CUSTOM_PROMPT_ID) showPromptSheet = false
            },
            onDismiss = { showPromptSheet = false },
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
            Text(submitButtonLabel(state, imageCount))
        }

        val failure = state as? SubmitState.Failed
        if (failure != null) {
            Text("Submit failed: ${failure.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ImageHero(
    imageUris: List<Uri>,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickRecent: (Int) -> Unit,
    onRemove: (Uri) -> Unit,
) {
    when {
        imageUris.isEmpty() -> EmptyHero(
            recents = recents,
            isFetchingRecent = isFetchingRecent,
            onPickGallery = onPickGallery,
            onTakePhoto = onTakePhoto,
            onPickRecent = onPickRecent,
        )
        imageUris.size == 1 -> Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUris[0],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        else -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${imageUris.size} images · same prompt",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(imageUris, key = { it.toString() }) { uri ->
                    ImageThumb(uri = uri, onRemove = { onRemove(uri) })
                }
                if (imageUris.size < MAX_BATCH_IMAGES) {
                    item(key = "__add_more__") {
                        AddMoreTile(onClick = onPickGallery)
                    }
                }
            }
            // Reserve space below the strip for the focused image too.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageUris[0],
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun EmptyHero(
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickRecent: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onPickGallery),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(
                text = "Tap to add a photo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.heightIn(min = 4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                IconLabel(
                    icon = Icons.Default.PhotoCamera,
                    label = "Take photo",
                    onClick = onTakePhoto,
                )
                IconLabel(
                    icon = Icons.Default.Image,
                    label = "From gallery",
                    onClick = onPickGallery,
                )
            }
            if (recents.isNotEmpty()) {
                Spacer(Modifier.heightIn(min = 16.dp))
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.heightIn(min = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recents.forEach { (id, model, selected) ->
                        Box(modifier = Modifier.size(64.dp)) {
                            AsyncImage(
                                model = model,
                                contentDescription = "Recent upload",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .combinedClickable(
                                        enabled = !isFetchingRecent,
                                        onClick = { onPickRecent(id) },
                                        onLongClick = {},
                                    ),
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Already selected",
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CompactSourceRow(
    imageCount: Int,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickRecent: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onTakePhoto) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Take photo")
        }
        IconButton(onClick = onPickGallery) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add image")
        }
        Spacer(Modifier.width(4.dp))
        // Inline recent thumbnails (small).
        recents.take(3).forEach { (id, model, selected) ->
            Box(modifier = Modifier.size(36.dp)) {
                AsyncImage(
                    model = model,
                    contentDescription = "Recent upload",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            enabled = !isFetchingRecent,
                            onClick = { onPickRecent(id) },
                            onLongClick = {},
                        ),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.weight(1f))
        if (imageCount > 1) {
            Text(
                text = "$imageCount selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
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
        shape = RoundedCornerShape(8.dp),
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
                    text = "Prompt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = selectedLabel ?: "Choose a prompt",
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
                        text = "HQ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Open prompt picker",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptLibrarySheet(
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    tryHarder: Boolean,
    onTryHarderChange: (Boolean) -> Unit,
    deleteMode: Boolean,
    onLongPressChip: () -> Unit,
    onExitDeleteMode: () -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onSavePromptClick: () -> Unit,
    onSelectPrompt: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val builtIns = prompts.filter { it.id != CUSTOM_PROMPT_ID }
    val filtered = remember(query, builtIns) {
        if (query.isBlank()) {
            builtIns
        } else {
            builtIns.filter { it.label.contains(query.trim(), ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onExitDeleteMode()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Choose a prompt",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (deleteMode) {
                    TextButton(onClick = onExitDeleteMode) { Text("Done") }
                }
            }

            // High-quality toggle (was "Try Harder").
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "High quality",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Slower, larger model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Switch(checked = tryHarder, onCheckedChange = onTryHarderChange)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search prompts") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Custom prompt always at top, expanded when selected.
            CustomPromptItem(
                expanded = selectedPromptId == CUSTOM_PROMPT_ID,
                customText = customPromptText,
                onClick = { onSelectPrompt(CUSTOM_PROMPT_ID) },
                onTextChange = onCustomPromptChange,
                onSaveClick = onSavePromptClick,
            )

            if (deleteMode) {
                Text(
                    text = "Tap × to remove a prompt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                items(filtered, key = { it.id }) { prompt ->
                    PromptRow(
                        label = prompt.label,
                        description = prompt.description,
                        selected = selectedPromptId == prompt.id,
                        showDelete = deleteMode,
                        onClick = { onSelectPrompt(prompt.id) },
                        onLongClick = onLongPressChip,
                        onDelete = { onDeletePrompt(prompt.id) },
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "No prompts match \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PromptRow(
    label: String,
    description: String?,
    selected: Boolean,
    showDelete: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete prompt",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomPromptItem(
    expanded: Boolean,
    customText: String,
    onClick: () -> Unit,
    onTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (expanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expanded) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "Write your own…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (expanded) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (expanded) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = onTextChange,
                    placeholder = { Text("e.g. A cat wearing a spacesuit") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (customText.isNotBlank()) {
                            IconButton(onClick = onSaveClick) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = "Save prompt",
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HealthDot(health: HealthState) {
    val color = when (health) {
        HealthState.Checking -> MaterialTheme.colorScheme.outlineVariant
        HealthState.Connected -> Color(0xFF1D9E75)
        HealthState.Unauthorized,
        is HealthState.NetworkError,
        is HealthState.ServerError,
        -> MaterialTheme.colorScheme.error
    }
    val description = when (health) {
        HealthState.Checking -> "Checking connection"
        HealthState.Connected -> "Connected"
        HealthState.Unauthorized -> "Invalid API token"
        is HealthState.NetworkError -> health.message
        is HealthState.ServerError -> "Server error ${health.code}"
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = description },
    )
}

private const val MAX_BATCH_IMAGES = 20

/**
 * Show a snackbar but dismiss any in-flight one first, so rapid taps don't
 * pile up a multi-second queue of identical messages.
 */
private suspend fun SnackbarHostState.showOne(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}

private val UriListSaver: Saver<List<Uri>, Any> = listSaver(
    save = { it.toList() },
    restore = { it },
)

private fun submitButtonLabel(state: SubmitState, count: Int): String = when {
    state is SubmitState.InFlight -> "Submitting…"
    count > 1 -> "Generate $count"
    else -> "Generate"
}

@Composable
private fun UploadProgressSection(
    uploadProgress: Float?,
    batchProgress: BatchProgress?,
) {
    if (uploadProgress == null && batchProgress == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = buildString {
            if (batchProgress != null) {
                append("Uploading ${batchProgress.current} of ${batchProgress.total}")
                if (uploadProgress != null) append(" · ${(uploadProgress * 100).toInt()}%")
            } else if (uploadProgress != null) {
                append("Uploading… ${(uploadProgress * 100).toInt()}%")
            }
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

@Composable
private fun ImageThumb(uri: Uri, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(96.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                .combinedClickable(onClick = onRemove, onLongClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove image",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun AddMoreTile(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(96.dp)
            .combinedClickable(onClick = onClick, onLongClick = {}),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add more images",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Add more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
