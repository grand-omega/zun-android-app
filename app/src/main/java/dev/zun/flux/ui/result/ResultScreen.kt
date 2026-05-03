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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
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
import dev.zun.flux.util.resolvePromptLabel
import dev.zun.flux.util.saveToPictures
import dev.zun.flux.util.shareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

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
    var showEditPrompt by remember { mutableStateOf(false) }
    val prompts by repository.promptsState.collectAsState()
    val isWide = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    val promptLabel = jobDto?.let { resolvePromptLabel(prompts, it.effectivePromptId, it.prompt_text) }
        ?: "Loading…"

    // Initial text for the edit dialog: existing free-text, or the label of
    // the preset (the user can rewrite it from there).
    val editInitial = jobDto?.prompt_text?.takeIf { it.isNotBlank() } ?: promptLabel

    val canRegenerate = jobDto?.input_id != null && !regenerating

    val launchRegenerate: (String?) -> Unit = { overrideText ->
        val dto = jobDto
        val inputId = dto?.input_id
        if (dto != null && inputId != null && !regenerating) {
            regenerating = true
            scope.launch {
                try {
                    val inputUri = withContext(Dispatchers.IO) {
                        repository.downloadInputToCache(inputId)
                    }
                    val resp = when {
                        overrideText != null -> repository.submitJob(
                            inputUri = inputUri,
                            promptText = overrideText,
                            workflow = dto.workflow,
                        )
                        dto.effectivePromptId != null -> repository.submitJob(
                            inputUri = inputUri,
                            promptId = dto.effectivePromptId,
                            workflow = dto.workflow,
                        )
                        else -> repository.submitJob(
                            inputUri = inputUri,
                            promptText = dto.prompt_text.orEmpty(),
                            workflow = dto.workflow,
                        )
                    }
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
                onEdit = { showEditPrompt = true },
                editEnabled = canRegenerate,
            )

            // Primary action: regenerate with same params. Disabled when there's
            // no input image (text-only generations can't be re-run from here).
            Button(
                onClick = { launchRegenerate(null) },
                enabled = canRegenerate,
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

    if (showEditPrompt) {
        EditPromptDialog(
            initialText = editInitial,
            onDismiss = { showEditPrompt = false },
            onRun = { newText ->
                showEditPrompt = false
                launchRegenerate(newText)
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
private fun EditPromptDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit prompt") },
        text = {
            Column {
                Text(
                    text = "Re-runs on the same input image.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onRun(text.trim()) },
            ) { Text("Run") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
