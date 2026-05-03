package dev.zun.flux.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.util.formatTimestamp
import dev.zun.flux.util.resolvePromptLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Whether a drag-select session is adding tiles to or removing them from the
 *  base selection. Determined by the state of the anchor tile at long-press. */
private enum class DragMode { Add, Remove }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    repository: JobRepository,
    viewModel: GalleryViewModel,
    onJobClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val eventMessage by viewModel.eventMessage.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val postSaveDelete by viewModel.postSaveDelete.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val latestSelectedIds by rememberUpdatedState(selectedIds)
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(eventMessage) {
        eventMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearEventMessage()
        }
    }

    LaunchedEffect(pendingUndo) {
        val undo = pendingUndo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted ${undo.size} generation${if (undo.size == 1) "" else "s"}",
            actionLabel = "Undo",
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
            viewModel.undoDelete(undo)
        } else {
            viewModel.clearPendingUndo()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }, enabled = !isSaving) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.saveSelected(context) }, enabled = !isSaving) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = "Save selected")
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = !isSaving) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Gallery") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter by tag")
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                            ) {
                                availableTags.forEach { option ->
                                    val isSelected = option.filter == tagFilter
                                    DropdownMenuItem(
                                        text = { Text("${option.label} · ${option.count}") },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            viewModel.setTagFilter(option.filter)
                                            showFilterMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { inner ->
        Column(modifier = Modifier.padding(inner)) {
            if (tagFilter != TagFilter.All && !isSelectionMode) {
                val activeLabel = availableTags.firstOrNull { it.filter == tagFilter }?.label
                    ?: "Filtered"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AssistChip(
                        onClick = { viewModel.setTagFilter(TagFilter.All) },
                        label = { Text("Showing: $activeLabel") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear filter",
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (jobs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            isLoading -> CircularProgressIndicator()
                            tagFilter != TagFilter.All -> Text("No generations match this tag")
                            else -> Text("No generations yet")
                        }
                    }
                } else {
                    val groupedJobs =
                        remember(jobs) {
                            jobs.groupBy { formatTimestamp(it.created_at) }
                        }
                    val gridState = rememberLazyGridState()
                    // Tile bounds keyed by jobId, in root coords. Updated on
                    // every layout pass (so scroll keeps them fresh).
                    val tileBounds = remember { mutableStateMapOf<String, Rect>() }
                    var anchorIndex by remember { mutableStateOf<Int?>(null) }
                    var dragMode by remember { mutableStateOf(DragMode.Add) }
                    var baseSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
                    var pointerLocal by remember { mutableStateOf(Offset.Zero) }
                    var pointerRoot by remember { mutableStateOf(Offset.Zero) }
                    var viewportHeight by remember { mutableFloatStateOf(0f) }
                    var boxOriginInRoot by remember { mutableStateOf(Offset.Zero) }

                    fun applyRange(cursorIdx: Int) {
                        val anchor = anchorIndex ?: return
                        val lo = minOf(anchor, cursorIdx)
                        val hi = maxOf(anchor, cursorIdx)
                        val rangeIds = jobs.subList(lo, hi + 1).asSequence()
                            .map { it.id }
                            .toSet()
                        viewModel.setSelection(
                            if (dragMode == DragMode.Add) {
                                baseSelection + rangeIds
                            } else {
                                baseSelection - rangeIds
                            },
                        )
                    }

                    fun hitTest(rootPos: Offset): Int {
                        val id = tileBounds.entries
                            .firstOrNull { it.value.contains(rootPos) }?.key
                            ?: return -1
                        return jobs.indexOfFirst { it.id == id }
                    }

                    // Auto-scroll loop: while a drag is active, scroll the
                    // grid when the pointer is near the top/bottom edge.
                    LaunchedEffect(anchorIndex != null) {
                        if (anchorIndex == null) return@LaunchedEffect
                        val edgeZone = 80f
                        val pxPerFrame = 22f
                        while (isActive) {
                            val py = pointerLocal.y
                            val vh = viewportHeight
                            val direction = when {
                                py < edgeZone -> -1f
                                py > vh - edgeZone -> 1f
                                else -> 0f
                            }
                            if (direction != 0f) {
                                gridState.scrollBy(direction * pxPerFrame)
                                // After scroll, tile bounds shift; re-hit-test.
                                val idx = hitTest(pointerRoot)
                                if (idx >= 0) applyRange(idx)
                            }
                            delay(16)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coords ->
                                boxOriginInRoot = coords.positionInRoot()
                                viewportHeight = coords.size.height.toFloat()
                            },
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            groupedJobs.forEach { (date, items) ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                                items(items, key = { it.id }) { job ->
                                    val isSelected = selectedIds.contains(job.id)
                                    val jobIndex = jobs.indexOfFirst { it.id == job.id }
                                    JobThumbnail(
                                        modifier = Modifier
                                            .onGloballyPositioned { coords ->
                                                tileBounds[job.id] = coords.boundsInRoot()
                                            }
                                            .pointerInput(job.id, jobIndex) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { local ->
                                                        if (jobIndex < 0) return@detectDragGesturesAfterLongPress
                                                        val tileBoundsForJob = tileBounds[job.id]
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        val root = tileBoundsForJob.topLeft + local
                                                        pointerRoot = root
                                                        pointerLocal = root - boxOriginInRoot
                                                        baseSelection = latestSelectedIds
                                                        dragMode = if (job.id in baseSelection) {
                                                            DragMode.Remove
                                                        } else {
                                                            DragMode.Add
                                                        }
                                                        anchorIndex = jobIndex
                                                        applyRange(jobIndex)
                                                    },
                                                    onDrag = { change, _ ->
                                                        if (anchorIndex == null) return@detectDragGesturesAfterLongPress
                                                        val tileBoundsForJob = tileBounds[job.id]
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        val root = tileBoundsForJob.topLeft + change.position
                                                        pointerRoot = root
                                                        pointerLocal = root - boxOriginInRoot
                                                        val idx = hitTest(root)
                                                        if (idx >= 0) applyRange(idx)
                                                        change.consume()
                                                    },
                                                    onDragEnd = { anchorIndex = null },
                                                    onDragCancel = { anchorIndex = null },
                                                )
                                            },
                                        job = job,
                                        prompts = prompts,
                                        model = repository.thumbModel(job.id),
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(job.id)
                                            } else {
                                                onJobClick(job.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete selected?") },
            text = { Text("Removes ${selectedIds.size} generations. You can undo within 30 days.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirm = false
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    postSaveDelete?.let { savedIds ->
        val n = savedIds.size
        AlertDialog(
            onDismissRequest = { viewModel.dismissPostSaveDelete() },
            title = { Text("Remove from app?") },
            text = {
                Text(
                    "Saved $n image${if (n == 1) "" else "s"} to your gallery. " +
                        "Remove ${if (n == 1) "it" else "them"} from the app to free up space?",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPostSaveDelete() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPostSaveDelete() }) {
                    Text("Keep")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JobThumbnail(
    job: JobSummaryDto,
    prompts: List<PromptDto>,
    model: Any?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
            ),
        colors =
        CardDefaults.cardColors(
            containerColor =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border =
        if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isSelected) Modifier.padding(8.dp).clip(RoundedCornerShape(4.dp)) else Modifier,
                    ),
            )

            if (!isSelectionMode) {
                Text(
                    text = resolvePromptLabel(prompts, job.prompt_id, job.prompt_text),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
