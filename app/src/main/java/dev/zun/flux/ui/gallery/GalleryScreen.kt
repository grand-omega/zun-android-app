package dev.zun.flux.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import dev.zun.flux.R
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.OfflineImageAvailability
import dev.zun.flux.ui.common.EmptyState
import dev.zun.flux.ui.common.MissingImageState
import dev.zun.flux.util.resolvePromptLabel

/** Whether a drag-select session is adding tiles to or removing them from the
 *  base selection. Determined by the state of the anchor tile at long-press. */
private enum class DragMode { Add, Remove }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    images: ImageSourceRepository,
    viewModel: GalleryViewModel,
    onJobClick: (String) -> Unit,
    onBack: () -> Unit,
    showUndoSnackbars: Boolean = true,
) {
    val pagedItems = viewModel.pagedGridItems.collectAsLazyPagingItems()
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isSharing by viewModel.isSharing.collectAsStateWithLifecycle()
    val eventMessage by viewModel.eventMessage.collectAsStateWithLifecycle()
    val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
    val postSaveDelete by viewModel.postSaveDelete.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val latestSelectedIds by rememberUpdatedState(selectedIds)
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showImageMetadata by remember { mutableStateOf(false) }

    BackHandler(isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(eventMessage) {
        eventMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearEventMessage()
        }
    }

    LaunchedEffect(pendingUndo, showUndoSnackbars) {
        if (!showUndoSnackbars) return@LaunchedEffect
        val undo = pendingUndo ?: return@LaunchedEffect
        val message = if (undo.size == 1) {
            context.getString(R.string.gallery_undo_deleted_one)
        } else {
            context.getString(R.string.gallery_undo_deleted_many, undo.size)
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = context.getString(R.string.gallery_undo),
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
                    title = { Text(stringResource(R.string.gallery_n_selected, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }, enabled = !isSaving) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.gallery_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.saveSelected(context) },
                            enabled = !isSaving && !isSharing,
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.gallery_save_selected))
                            }
                        }
                        IconButton(
                            onClick = { viewModel.shareSelected(context) },
                            enabled = !isSaving && !isSharing,
                        ) {
                            if (isSharing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.gallery_share_selected))
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, enabled = !isSaving && !isSharing) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.gallery_delete_selected))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.gallery_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showImageMetadata = !showImageMetadata }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Label,
                                contentDescription = stringResource(
                                    if (showImageMetadata) {
                                        R.string.gallery_hide_image_labels
                                    } else {
                                        R.string.gallery_show_image_labels
                                    },
                                ),
                                tint = if (showImageMetadata) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.gallery_filter_by_tag))
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                            ) {
                                availableTags.forEach { option ->
                                    val isSelected = option.filter == tagFilter
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    R.string.gallery_filter_option_format,
                                                    option.label,
                                                    option.count,
                                                ),
                                            )
                                        },
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
                    ?: stringResource(R.string.gallery_filtered)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AssistChip(
                        onClick = { viewModel.setTagFilter(TagFilter.All) },
                        label = { Text(stringResource(R.string.gallery_showing_format, activeLabel)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.gallery_clear_filter),
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
                val isInitialLoading = pagedItems.loadState.refresh is LoadState.Loading
                val refreshError = pagedItems.loadState.refresh as? LoadState.Error
                val isEmpty = pagedItems.itemCount == 0 &&
                    pagedItems.loadState.refresh is LoadState.NotLoading
                if (refreshError != null && pagedItems.itemCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Default.CloudOff,
                            title = stringResource(R.string.gallery_load_error_title),
                            message = refreshError.error.message
                                ?: stringResource(R.string.gallery_load_error_default),
                            action = {
                                TextButton(onClick = { pagedItems.retry() }) {
                                    Text(stringResource(R.string.common_retry))
                                }
                            },
                        )
                    }
                } else if (isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isLoading || isInitialLoading) {
                            CircularProgressIndicator()
                        } else {
                            EmptyState(
                                icon = Icons.Default.ImageNotSupported,
                                title = stringResource(
                                    if (tagFilter != TagFilter.All) {
                                        R.string.gallery_empty_filtered_title
                                    } else {
                                        R.string.gallery_empty_title
                                    },
                                ),
                                message = stringResource(
                                    if (tagFilter != TagFilter.All) {
                                        R.string.gallery_empty_filtered_message
                                    } else {
                                        R.string.gallery_empty_message
                                    },
                                ),
                                action = {
                                    TextButton(
                                        onClick = {
                                            if (tagFilter != TagFilter.All) {
                                                viewModel.setTagFilter(TagFilter.All)
                                            } else {
                                                onBack()
                                            }
                                        },
                                    ) {
                                        Text(
                                            stringResource(
                                                if (tagFilter != TagFilter.All) {
                                                    R.string.gallery_clear_filter
                                                } else {
                                                    R.string.gallery_create_an_edit
                                                },
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    // Tile bounds keyed by jobId, in root coords. Updated on
                    // every layout pass (so scroll keeps them fresh).
                    val tileBounds = remember { mutableStateMapOf<String, Rect>() }
                    var anchorIndex by remember { mutableStateOf<Int?>(null) }
                    var dragMode by remember { mutableStateOf(DragMode.Add) }
                    var baseSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

                    fun applyRange(cursorIdx: Int) {
                        val anchor = anchorIndex ?: return
                        val lo = minOf(anchor, cursorIdx)
                        val hi = maxOf(anchor, cursorIdx)
                        val snapshot = pagedItems.itemSnapshotList.items
                        val rangeIds = (lo..hi).asSequence()
                            .mapNotNull { (snapshot.getOrNull(it) as? GalleryGridItem.JobItem)?.job?.id }
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
                        return pagedItems.itemSnapshotList.items.indexOfFirst {
                            it is GalleryGridItem.JobItem && it.job.id == id
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(16.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                count = pagedItems.itemCount,
                                key = { idx ->
                                    when (val item = pagedItems.peek(idx)) {
                                        is GalleryGridItem.JobItem -> "job_${item.job.id}"
                                        is GalleryGridItem.DateSeparator -> "sep_${item.date}"
                                        null -> "ph_$idx"
                                    }
                                },
                                span = { idx ->
                                    when (pagedItems.peek(idx)) {
                                        is GalleryGridItem.DateSeparator -> GridItemSpan(maxLineSpan)
                                        else -> GridItemSpan(1)
                                    }
                                },
                            ) { idx ->
                                when (val item = pagedItems[idx]) {
                                    is GalleryGridItem.DateSeparator -> {
                                        Text(
                                            text = item.date,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        )
                                    }

                                    is GalleryGridItem.JobItem -> {
                                        val job = item.job
                                        val isSelected = selectedIds.contains(job.id)
                                        val jobIndex = idx
                                        JobThumbnail(
                                            modifier = Modifier
                                                .onGloballyPositioned { coords ->
                                                    tileBounds[job.id] = coords.boundsInRoot()
                                                }
                                                .pointerInput(job.id, jobIndex) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = {
                                                            if (jobIndex < 0) return@detectDragGesturesAfterLongPress
                                                            tileBounds[job.id]
                                                                ?: return@detectDragGesturesAfterLongPress
                                                            baseSelection = latestSelectedIds
                                                            dragMode = if (job.id in baseSelection) {
                                                                DragMode.Remove
                                                            } else {
                                                                DragMode.Add
                                                            }
                                                            anchorIndex = jobIndex
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            applyRange(jobIndex)
                                                        },
                                                        onDrag = { change, _ ->
                                                            if (anchorIndex == null) return@detectDragGesturesAfterLongPress
                                                            val tileBoundsForJob = tileBounds[job.id]
                                                                ?: return@detectDragGesturesAfterLongPress
                                                            val root = tileBoundsForJob.topLeft + change.position
                                                            val hit = hitTest(root)
                                                            if (hit >= 0) applyRange(hit)
                                                            change.consume()
                                                        },
                                                        onDragEnd = { anchorIndex = null },
                                                        onDragCancel = { anchorIndex = null },
                                                    )
                                                },
                                            job = job,
                                            prompts = prompts,
                                            model = images.thumbModel(job.id),
                                            availability = images.offlineAvailability(job.id),
                                            showMetadata = showImageMetadata,
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

                                    null -> { /* placeholder while loading */ }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteSelectedDialog(
            selectedCount = selectedIds.size,
            onConfirm = {
                viewModel.deleteSelected()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    postSaveDelete?.let { savedIds ->
        PostSaveDeleteDialog(
            savedCount = savedIds.size,
            onConfirm = { viewModel.confirmPostSaveDelete() },
            onDismiss = { viewModel.dismissPostSaveDelete() },
        )
    }
}
