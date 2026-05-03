package dev.zun.flux.ui.result

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.ui.gallery.BeforeAfterSlider
import dev.zun.flux.ui.home.CUSTOM_PROMPT_ID
import dev.zun.flux.ui.home.PromptLibrarySheet
import dev.zun.flux.ui.home.PromptManageSheet
import dev.zun.flux.util.resolvePromptLabel
import dev.zun.flux.util.saveToPictures
import dev.zun.flux.util.shareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

private const val DEFAULT_CUSTOM_WORKFLOW = "flux2_klein_edit"
private const val TRY_HARDER_WORKFLOW = "flux2_klein_9b_kv_experimental"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    jobId: String,
    repository: JobRepository,
    windowSizeClass: WindowSizeClass,
    onRegenerated: (String) -> Unit,
    onNewImage: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jobDto by produceState<JobStatusDto?>(null, jobId) {
        repository.getJobFlow(jobId).collect { value = it }
    }
    val inputModel = remember(jobDto?.input_id) { repository.inputModel(jobDto?.input_id) }
    val previewModel = remember(jobId) { repository.previewModel(jobId) }
    val resultModel = remember(jobId) { repository.resultModel(jobId) }

    var saving by remember { mutableStateOf(false) }
    var regenerating by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showPromptSheet by remember { mutableStateOf(false) }
    var showPromptManageSheet by remember { mutableStateOf(false) }
    var showSavePromptDialog by remember { mutableStateOf(false) }
    var savePromptLabel by remember { mutableStateOf("") }
    var selectedPromptId by remember(jobId) { mutableStateOf<Long?>(null) }
    var customPromptText by remember(jobId) { mutableStateOf("") }
    var tryHarder by remember(jobId) { mutableStateOf(false) }
    val prompts by repository.promptsState.collectAsState()
    val isWide = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    LaunchedEffect(jobDto?.id) {
        val dto = jobDto ?: return@LaunchedEffect
        selectedPromptId = dto.effectivePromptId ?: dto.prompt_text
            ?.takeIf { it.isNotBlank() }
            ?.let { CUSTOM_PROMPT_ID }
        customPromptText = dto.prompt_text.orEmpty()
        tryHarder = dto.workflow == TRY_HARDER_WORKFLOW
    }

    val promptLabel = when (val id = selectedPromptId) {
        null -> jobDto?.let { resolvePromptLabel(prompts, it.effectivePromptId, it.prompt_text) } ?: "Loading…"
        CUSTOM_PROMPT_ID -> customPromptText.trim().takeIf { it.isNotBlank() }?.let {
            if (it.length <= 64) it else it.take(61) + "…"
        } ?: "Write your own..."
        else -> prompts.firstOrNull { it.id == id }?.label
            ?: jobDto?.let { resolvePromptLabel(prompts, it.effectivePromptId, it.prompt_text) }
            ?: "Choose a prompt"
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
                        repository.downloadInputToCache(inputId)
                    }
                    val resp = repository.submitJob(
                        inputUri = inputUri,
                        promptId = promptId.takeUnless { it == CUSTOM_PROMPT_ID },
                        promptText = customText.takeIf { promptId == CUSTOM_PROMPT_ID },
                        workflow = if (tryHarder) TRY_HARDER_WORKFLOW else DEFAULT_CUSTOM_WORKFLOW,
                    )
                    onRegenerated(resp.job_id)
                } catch (t: Throwable) {
                    Toast.makeText(
                        context,
                        "Regenerate failed: ${t.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                } finally {
                    regenerating = false
                }
            }
        }
    }

    fun deleteResult() {
        scope.launch {
            try {
                repository.deleteJob(jobId)
                onDeleted()
            } catch (t: Throwable) {
                Toast.makeText(
                    context,
                    "Delete failed: ${t.message}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("New image") },
                            onClick = {
                                showMenu = false
                                onNewImage()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("View details") },
                            onClick = {
                                showMenu = false
                                showDetails = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                deleteResult()
                            },
                        )
                    }
                },
            )
        },
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
                        label = "Before",
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    CompareImage(
                        model = previewModel,
                        label = "After",
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            } else {
                var sliderProgress by remember { mutableFloatStateOf(0.5f) }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (inputModel != null) {
                        BeforeAfterSlider(
                            beforeModel = inputModel,
                            afterModel = previewModel,
                            progress = sliderProgress,
                            onProgressChange = { sliderProgress = it },
                        )
                    } else {
                        // No input image (e.g. text-only generation) — just show the result.
                        CompareImage(model = previewModel, label = "After", modifier = Modifier.fillMaxSize())
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
                        text = "Regenerating…",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Regenerate")
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
                                    "Saved to Pictures/FluxEdit"
                                } catch (t: Throwable) {
                                    "Save failed: ${t.message}"
                                }
                            saving = false
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = resultModel != null && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "Saving…" else "Save")
                }

                OutlinedButton(
                    onClick = {
                        val src = resultModel ?: return@OutlinedButton
                        scope.launch {
                            try {
                                shareImage(context, src)
                            } catch (t: Throwable) {
                                Toast.makeText(
                                    context,
                                    "Share failed: ${t.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                    Text("Share")
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
        )
    }

    if (showPromptManageSheet) {
        PromptManageSheet(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            onDeletePrompt = { promptId ->
                scope.launch {
                    try {
                        repository.deletePrompt(promptId)
                        if (selectedPromptId == promptId) selectedPromptId = null
                    } catch (t: Throwable) {
                        Toast.makeText(
                            context,
                            "Delete prompt failed: ${t.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onDismiss = { showPromptManageSheet = false },
        )
    }

    if (showSavePromptDialog) {
        AlertDialog(
            onDismissRequest = { showSavePromptDialog = false },
            title = { Text("Save this prompt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = customPromptText.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = savePromptLabel,
                        onValueChange = { savePromptLabel = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = savePromptLabel.isNotBlank() && customPromptText.isNotBlank(),
                    onClick = {
                        val label = savePromptLabel.trim()
                        val text = customPromptText.trim()
                        scope.launch {
                            try {
                                val created = repository.createPrompt(
                                    label = label,
                                    text = text,
                                    workflow = DEFAULT_CUSTOM_WORKFLOW,
                                )
                                selectedPromptId = created.id
                                customPromptText = ""
                                showSavePromptDialog = false
                                showPromptSheet = false
                                Toast.makeText(context, "Prompt saved", Toast.LENGTH_SHORT).show()
                            } catch (t: Throwable) {
                                Toast.makeText(
                                    context,
                                    "Save prompt failed: ${t.message}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePromptDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDetails) {
        val dto = jobDto
        val locale = LocalLocale.current.platformLocale
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dto != null) {
                        Text("Prompt: ${resolvePromptLabel(prompts, dto.effectivePromptId, dto.prompt_text)}")
                        Text("Created: ${SimpleDateFormat("MMM d, yyyy · HH:mm", locale).format(Date(dto.created_at * 1000))}")
                        val started = dto.started_at
                        val completed = dto.completed_at
                        if (started != null && completed != null) {
                            Text("Duration: ${completed - started}s")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text("Close") }
            },
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
        shape = RoundedCornerShape(8.dp),
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
                Icon(Icons.Default.Edit, contentDescription = "Edit prompt and regenerate")
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
