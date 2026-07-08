package dev.zun.flux.ui.result

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zun.flux.R
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.Workflows
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.PromptSelection
import dev.zun.flux.data.repo.SettingsManager
import dev.zun.flux.data.repo.UploadRepository
import dev.zun.flux.ui.common.BackNavigationIcon
import dev.zun.flux.ui.common.ControlShape
import dev.zun.flux.ui.gallery.CompareMode
import dev.zun.flux.ui.gallery.CompareModeSwitcher
import dev.zun.flux.ui.home.CUSTOM_PROMPT_ID
import dev.zun.flux.ui.home.PromptLibrarySheet
import dev.zun.flux.ui.home.PromptManageSheet
import dev.zun.flux.util.resolvePromptLabel
import dev.zun.flux.util.saveToPictures
import dev.zun.flux.util.shareImage
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

private const val DEFAULT_CUSTOM_WORKFLOW = Workflows.DEFAULT_EDIT
private const val TRY_HARDER_WORKFLOW = Workflows.TRY_HARDER_EDIT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    jobId: String,
    jobs: JobRepository,
    promptRepo: PromptRepository,
    uploads: UploadRepository,
    images: ImageSourceRepository,
    onRegenerated: (String) -> Unit,
    onNewImage: () -> Unit,
    onUseAsSource: (Uri) -> Unit,
    onDeleted: () -> Unit,
    onViewEditHistory: (String) -> Unit,
    onBack: () -> Unit,
    settings: SettingsManager? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jobDto by produceState<JobStatusDto?>(null, jobId) {
        jobs.getJobFlow(jobId).collect { value = it }
    }
    val lineageRootId by produceState<String?>(null, jobId) {
        value = jobs.getLineageRootId(jobId)
    }
    val inputModel = remember(jobDto?.input_id) { images.inputModel(jobDto?.input_id) }
    val previewModel = remember(jobId) { images.previewModel(jobId) }
    val resultModel = remember(jobId) { images.resultModel(jobId) }

    var saving by remember { mutableStateOf(false) }
    var regenerating by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPromptSheet by remember { mutableStateOf(false) }
    var showPromptManageSheet by remember { mutableStateOf(false) }
    var showSavePromptDialog by remember { mutableStateOf(false) }
    var savePromptLabel by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        val msg = notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        notice = null
    }
    val savedToPicturesMessage = stringResource(R.string.result_saved_to_pictures)
    val promptSavedMessage = stringResource(R.string.result_prompt_saved)
    var selectedPromptId by remember(jobId) { mutableStateOf<Long?>(null) }
    var customPromptText by remember(jobId) { mutableStateOf("") }
    var tryHarder by remember(jobId) { mutableStateOf(false) }
    val prompts by promptRepo.promptsState.collectAsState()
    val app = context.applicationContext as dev.zun.flux.FluxApp
    val pinnedIds by app.pinnedPrompts.ids.collectAsState()
    // Server-supported workflows gate the Try-harder toggle (see /capabilities).
    val tryHarderAvailable by produceState(false) {
        value = runCatching { app.repositories.health.capabilities() }
            .getOrNull()
            ?.workflows
            ?.any { it.name == Workflows.TRY_HARDER_EDIT && it.supported }
            ?: false
    }
    val isWide = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(androidx.window.core.layout.WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    LaunchedEffect(jobDto?.id) {
        val dto = jobDto ?: return@LaunchedEffect
        selectedPromptId = dto.effectivePromptId ?: dto.prompt_text
            ?.takeIf { it.isNotBlank() }
            ?.let { CUSTOM_PROMPT_ID }
        customPromptText = dto.prompt_text.orEmpty()
        tryHarder = dto.workflow == TRY_HARDER_WORKFLOW
    }

    val promptLabel = when (val id = selectedPromptId) {
        null -> jobDto?.let { resolvePromptLabel(prompts, it.effectivePromptId, it.prompt_text) } ?: stringResource(R.string.result_loading)

        CUSTOM_PROMPT_ID -> customPromptText.trim().takeIf { it.isNotBlank() }?.let {
            if (it.length <= 64) it else it.take(61) + "…"
        } ?: stringResource(R.string.result_write_your_own)

        else -> prompts.firstOrNull { it.id == id }?.label
            ?: jobDto?.let { resolvePromptLabel(prompts, it.effectivePromptId, it.prompt_text) }
            ?: stringResource(R.string.result_choose_prompt)
    }

    val canRegenerate = jobDto?.input_id != null && !regenerating

    fun launchRegenerate() {
        val dto = jobDto
        val inputId = dto?.input_id
        val promptId = selectedPromptId
        val customText = customPromptText.trim()
        if (
            dto != null &&
            inputId != null &&
            promptId != null &&
            (promptId != CUSTOM_PROMPT_ID || customText.isNotBlank()) &&
            !regenerating
        ) {
            regenerating = true
            scope.launch {
                try {
                    val inputUri = withContext(Dispatchers.IO) {
                        images.downloadInputToCache(inputId)
                    }
                    val selection = if (promptId == CUSTOM_PROMPT_ID) {
                        PromptSelection.Custom(customText)
                    } else {
                        PromptSelection.Saved(promptId)
                    }
                    val resp = uploads.submitJob(
                        inputUri = inputUri,
                        selection = selection,
                        workflow = if (tryHarder) TRY_HARDER_WORKFLOW else DEFAULT_CUSTOM_WORKFLOW,
                    )
                    onRegenerated(resp.job_id)
                } catch (t: Throwable) {
                    notice = t.toUserMessage("regenerate")
                } finally {
                    regenerating = false
                }
            }
        }
    }

    fun deleteResult() {
        scope.launch {
            try {
                jobs.deleteJob(jobId)
                onDeleted()
            } catch (t: Throwable) {
                notice = t.toUserMessage("delete")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    BackNavigationIcon(onBack = onBack, contentDescription = stringResource(R.string.common_back))
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.result_new_image)) },
                            onClick = {
                                showMenu = false
                                onNewImage()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.result_use_as_source)) },
                            enabled = jobDto?.status == "done",
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    try {
                                        val uri = withContext(Dispatchers.IO) {
                                            images.downloadResultToCache(jobId)
                                        }
                                        onUseAsSource(uri)
                                    } catch (t: Throwable) {
                                        notice = t.toUserMessage("use the result as a new source")
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.result_view_details)) },
                            onClick = {
                                showMenu = false
                                showDetails = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.result_view_edit_history)) },
                            enabled = lineageRootId != null,
                            onClick = {
                                showMenu = false
                                lineageRootId?.let(onViewEditHistory)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.result_delete)) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            },
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isWide) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CompareImage(
                        model = inputModel,
                        label = stringResource(R.string.result_before),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    CompareImage(
                        model = previewModel,
                        label = stringResource(R.string.result_after),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (inputModel != null) {
                        CompareModeSwitcher(
                            beforeModel = inputModel,
                            afterModel = previewModel,
                            initialMode = if (settings?.defaultCompareModeIsScratch == true) {
                                CompareMode.Scratch
                            } else {
                                CompareMode.Slider
                            },
                            onSaveComposite = { bitmap -> jobs.saveLocalComposite(bitmap).map { } },
                        )
                    } else {
                        // No input image (e.g. text-only generation) — just show the result.
                        CompareImage(model = previewModel, label = stringResource(R.string.result_after), modifier = Modifier.fillMaxSize())
                    }
                }
            }

            PromptStrip(
                label = promptLabel,
                onEdit = { showPromptSheet = true },
                editEnabled = canRegenerate,
            )

            // Primary action: regenerate with same params. Disabled when there's
            // no input image (text-only generations can't be re-run from here).
            Button(
                onClick = { launchRegenerate() },
                enabled = canRegenerate &&
                    selectedPromptId != null &&
                    (selectedPromptId != CUSTOM_PROMPT_ID || customPromptText.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (regenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringResource(R.string.result_regenerating),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.result_regenerate))
                }
            }

            // Secondary actions: save / share.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val src = resultModel ?: return@OutlinedButton
                        saving = true
                        scope.launch {
                            val msg =
                                try {
                                    saveToPictures(context, src, "flux-$jobId.jpg")
                                    savedToPicturesMessage
                                } catch (t: Throwable) {
                                    t.toUserMessage("save")
                                }
                            saving = false
                            notice = msg
                        }
                    },
                    enabled = resultModel != null && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(if (saving) R.string.result_saving else R.string.result_save))
                }

                OutlinedButton(
                    onClick = {
                        val src = resultModel ?: return@OutlinedButton
                        scope.launch {
                            try {
                                shareImage(context, src)
                            } catch (t: Throwable) {
                                notice = t.toUserMessage("share")
                            }
                        }
                    },
                    enabled = resultModel != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.result_share))
                }
            }
        }
    }

    if (showPromptSheet) {
        PromptLibrarySheet(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            customPromptText = customPromptText,
            onCustomPromptChange = {
                customPromptText = it
                selectedPromptId = CUSTOM_PROMPT_ID
            },
            tryHarder = tryHarder,
            onTryHarderChange = { tryHarder = it },
            showTryHarder = tryHarderAvailable,
            onSavePromptClick = {
                savePromptLabel = ""
                showSavePromptDialog = true
            },
            onManagePrompts = {
                showPromptSheet = false
                showPromptManageSheet = true
            },
            onSelectPrompt = { id ->
                selectedPromptId = id
                if (id != CUSTOM_PROMPT_ID) showPromptSheet = false
            },
            onDismiss = { showPromptSheet = false },
            pinnedIds = pinnedIds,
            onTogglePin = { app.pinnedPrompts.toggle(it) },
        )
    }

    if (showDeleteConfirm) {
        DeleteResultDialog(
            onConfirm = {
                showDeleteConfirm = false
                deleteResult()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showPromptManageSheet) {
        PromptManageSheet(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            onDeletePrompt = { promptId ->
                scope.launch {
                    try {
                        promptRepo.deletePrompt(promptId)
                        if (selectedPromptId == promptId) selectedPromptId = null
                    } catch (t: Throwable) {
                        // Toast, not snackbar: the manage sheet stays open and
                        // would cover a Scaffold-hosted snackbar.
                        Toast.makeText(
                            context,
                            t.toUserMessage("delete prompt"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onUpdatePrompt = { promptId, label, text ->
                scope.launch {
                    try {
                        promptRepo.updatePrompt(promptId, label.trim(), text.trim())
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            t.toUserMessage("update prompt"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onDismiss = { showPromptManageSheet = false },
        )
    }

    if (showSavePromptDialog) {
        SavePromptDialog(
            customPromptText = customPromptText,
            label = savePromptLabel,
            onLabelChange = { savePromptLabel = it },
            onSave = {
                val label = savePromptLabel.trim()
                val text = customPromptText.trim()
                scope.launch {
                    try {
                        val created = promptRepo.createPrompt(
                            label = label,
                            text = text,
                            workflow = DEFAULT_CUSTOM_WORKFLOW,
                        )
                        selectedPromptId = created.id
                        customPromptText = ""
                        showSavePromptDialog = false
                        showPromptSheet = false
                        notice = promptSavedMessage
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            t.toUserMessage("save prompt"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onDismiss = { showSavePromptDialog = false },
        )
    }

    if (showDetails) {
        ResultDetailsDialog(
            jobDto = jobDto,
            prompts = prompts,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun PromptStrip(
    label: String,
    onEdit: () -> Unit,
    editEnabled: Boolean,
) {
    Surface(
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            IconButton(
                onClick = onEdit,
                enabled = editEnabled,
            ) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.result_edit_prompt_and_regenerate))
            }
        }
    }
}

@Composable
private fun CompareImage(
    model: Any?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
