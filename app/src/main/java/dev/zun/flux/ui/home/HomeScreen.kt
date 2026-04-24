package dev.zun.flux.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedPromptId by rememberSaveable { mutableStateOf<Long?>(null) }
    var customPromptText by rememberSaveable { mutableStateOf("") }
    var deleteMode by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var saveDialogLabel by rememberSaveable { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(capturedUri) {
        val src = capturedUri ?: return@LaunchedEffect
        imageUri = withContext(Dispatchers.IO) {
            runCatching { cacheInputLocally(context, src) }.getOrDefault(src)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.promptSavedEvents.collect { newId ->
            selectedPromptId = newId
            customPromptText = ""
            snackbarHostState.showSnackbar("Prompt saved")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.promptErrors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // If the selected prompt gets deleted, clear the selection.
    LaunchedEffect(prompts, selectedPromptId) {
        val current = selectedPromptId
        if (current != null && current != CUSTOM_PROMPT_ID && prompts.none { it.id == current }) {
            selectedPromptId = null
        }
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    imageUri = withContext(Dispatchers.IO) {
                        runCatching { cacheInputLocally(context, uri) }.getOrDefault(uri)
                    }
                }
            }
        }

    LaunchedEffect(state) {
        val s = state
        if (s is SubmitState.Done) {
            viewModel.acknowledgeDone()
            onJobSubmitted(s.jobId)
        }
    }

    val isWide = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("FluxEdit") },
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
                val uri = imageUri
                val promptId = selectedPromptId
                if (uri != null && promptId != null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.submit(uri, promptId, customPromptText)
                }
            }

            if (isWide) {
                WideHomeContent(
                    imageUri = imageUri,
                    prompts = prompts,
                    selectedPromptId = selectedPromptId,
                    customPromptText = customPromptText,
                    onCustomPromptChange = { customPromptText = it },
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
                    onTakePhoto = onTakePhoto,
                    onPickGallery = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onSelectPrompt = { selectedPromptId = it },
                    onSubmit = onSubmit,
                )
            } else {
                CompactHomeContent(
                    imageUri = imageUri,
                    prompts = prompts,
                    selectedPromptId = selectedPromptId,
                    customPromptText = customPromptText,
                    onCustomPromptChange = { customPromptText = it },
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
                    onTakePhoto = onTakePhoto,
                    onPickGallery = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onSelectPrompt = { selectedPromptId = it },
                    onSubmit = onSubmit,
                )
            }
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
private fun CompactHomeContent(
    imageUri: Uri?,
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    deleteMode: Boolean,
    onLongPressChip: () -> Unit,
    onExitDeleteMode: () -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onSavePromptClick: () -> Unit,
    state: SubmitState,
    health: HealthState,
    uploadProgress: Float?,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onSelectPrompt: (Long) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Image source", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                Text("Take photo")
            }
            OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f)) {
                Text("From gallery")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUri == null) {
                Text(
                    "No image selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        PromptPickerSection(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            customPromptText = customPromptText,
            onCustomPromptChange = onCustomPromptChange,
            deleteMode = deleteMode,
            onLongPressChip = onLongPressChip,
            onExitDeleteMode = onExitDeleteMode,
            onDeletePrompt = onDeletePrompt,
            onSavePromptClick = onSavePromptClick,
            onSelectPrompt = onSelectPrompt,
        )

        Spacer(Modifier.weight(1f))

        if (uploadProgress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Uploading… ${(uploadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        ConnectionIndicator(health)

        val canSubmit = imageUri != null &&
            selectedPromptId != null &&
            (selectedPromptId != CUSTOM_PROMPT_ID || customPromptText.isNotBlank()) &&
            state !is SubmitState.InFlight

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state is SubmitState.InFlight) "Submitting…" else "Generate")
        }

        val failure = state as? SubmitState.Failed
        if (failure != null) {
            Text("Submit failed: ${failure.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WideHomeContent(
    imageUri: Uri?,
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    deleteMode: Boolean,
    onLongPressChip: () -> Unit,
    onExitDeleteMode: () -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onSavePromptClick: () -> Unit,
    state: SubmitState,
    health: HealthState,
    uploadProgress: Float?,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onSelectPrompt: (Long) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Image source", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                    Text("Take photo")
                }
                OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f)) {
                    Text("From gallery")
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUri == null) {
                    Text(
                        "No image selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PromptPickerSection(
                prompts = prompts,
                selectedPromptId = selectedPromptId,
                customPromptText = customPromptText,
                onCustomPromptChange = onCustomPromptChange,
                deleteMode = deleteMode,
                onLongPressChip = onLongPressChip,
                onExitDeleteMode = onExitDeleteMode,
                onDeletePrompt = onDeletePrompt,
                onSavePromptClick = onSavePromptClick,
                onSelectPrompt = onSelectPrompt,
            )

            Spacer(Modifier.weight(1f))

            if (uploadProgress != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Uploading… ${(uploadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ConnectionIndicator(health)

            val canSubmit = imageUri != null &&
                selectedPromptId != null &&
                (selectedPromptId != CUSTOM_PROMPT_ID || customPromptText.isNotBlank()) &&
                state !is SubmitState.InFlight

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state is SubmitState.InFlight) "Submitting…" else "Generate")
            }

            val failure = state as? SubmitState.Failed
            if (failure != null) {
                Text("Submit failed: ${failure.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PromptPickerSection(
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    deleteMode: Boolean,
    onLongPressChip: () -> Unit,
    onExitDeleteMode: () -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onSavePromptClick: () -> Unit,
    onSelectPrompt: (Long) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Prompt", style = MaterialTheme.typography.labelLarge)
            if (deleteMode) {
                Text(
                    text = "Tap elsewhere to exit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            prompts.forEach { prompt ->
                val isCustom = prompt.id == CUSTOM_PROMPT_ID
                PromptChip(
                    label = prompt.label,
                    selected = selectedPromptId == prompt.id,
                    showDelete = deleteMode && !isCustom,
                    onClick = {
                        if (deleteMode) {
                            onExitDeleteMode()
                        } else {
                            onSelectPrompt(prompt.id)
                        }
                    },
                    onLongClick = {
                        if (!isCustom) onLongPressChip()
                    },
                    onDelete = { onDeletePrompt(prompt.id) },
                )
            }
        }

        if (selectedPromptId == CUSTOM_PROMPT_ID) {
            OutlinedTextField(
                value = customPromptText,
                onValueChange = onCustomPromptChange,
                label = { Text("Custom Prompt") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. A cat wearing a spacesuit") },
                trailingIcon = {
                    if (customPromptText.isNotBlank()) {
                        IconButton(onClick = onSavePromptClick) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PromptChip(
    label: String,
    selected: Boolean,
    showDelete: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(contentAlignment = Alignment.TopEnd) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = containerColor,
            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(top = 6.dp, end = 6.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }

        if (showDelete) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .combinedClickable(onClick = onDelete, onLongClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete prompt",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectionIndicator(health: HealthState) {
    val (color, text) = when (health) {
        HealthState.Checking -> MaterialTheme.colorScheme.outlineVariant to "Checking connection…"
        HealthState.Connected -> Color(0xFF1D9E75) to "Connected to tailnet"
        HealthState.Unauthorized -> MaterialTheme.colorScheme.error to "Invalid API Token"
        is HealthState.NetworkError -> MaterialTheme.colorScheme.error to health.message
        is HealthState.ServerError -> MaterialTheme.colorScheme.error to "Server Error (${health.code})"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
