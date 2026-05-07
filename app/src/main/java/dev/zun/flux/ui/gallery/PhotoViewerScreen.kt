package dev.zun.flux.ui.gallery

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import dev.zun.flux.R
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.OfflineImageAvailability
import dev.zun.flux.ui.common.ActionBarSurface
import dev.zun.flux.ui.common.MissingImageState
import dev.zun.flux.util.resolvePromptLabel
import dev.zun.flux.util.saveToPictures
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    initialJobId: String,
    viewModel: GalleryViewModel,
    repository: JobRepository,
    onUseInput: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val jobs by viewModel.jobs.collectAsState()
    val prompts by viewModel.prompts.collectAsState()

    val pagerState =
        rememberPagerState(
            initialPage = 0,
            pageCount = { jobs.size },
        )

    // jobs may still be loading on first entry, so the index isn't resolvable
    // at composition time. Scroll once it is — but only once, so user swipes
    // aren't yanked back.
    var didInitialScroll by rememberSaveable(initialJobId) { mutableStateOf(false) }
    LaunchedEffect(jobs, initialJobId) {
        if (didInitialScroll) return@LaunchedEffect
        val idx = jobs.indexOfFirst { it.id == initialJobId }
        if (idx >= 0) {
            pagerState.scrollToPage(idx)
            didInitialScroll = true
        }
    }

    var showDetails by remember { mutableStateOf(false) }
    var showCompare by remember { mutableStateOf(false) }
    var showUI by remember { mutableStateOf(true) }
    var zoomedPage by remember { mutableStateOf<String?>(null) }
    var selectingInput by remember { mutableStateOf(false) }
    var viewerNotice by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val pendingUndo by viewModel.pendingUndo.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentJob = jobs.getOrNull(pagerState.currentPage)
    val hasInput = currentJob?.input_id != null

    LaunchedEffect(pendingUndo) {
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

    LaunchedEffect(viewerNotice) {
        val notice = viewerNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
        viewerNotice = null
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = showUI,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showUI,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ViewerActionBar(
                    hasInput = hasInput,
                    selectingInput = selectingInput,
                    onCompare = { showCompare = true },
                    onUseInput = {
                        val inputId = currentJob?.input_id ?: return@ViewerActionBar
                        selectingInput = true
                        scope.launch {
                            try {
                                val uri = withContext(Dispatchers.IO) {
                                    repository.downloadInputToCache(inputId)
                                }
                                onUseInput(uri)
                            } catch (t: Throwable) {
                                Toast.makeText(
                                    context,
                                    t.toUserMessage("load the original input"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                selectingInput = false
                            }
                        }
                    },
                    onSave = {
                        val job = currentJob ?: return@ViewerActionBar
                        val src = repository.resultModel(job.id) ?: return@ViewerActionBar
                        scope.launch {
                            try {
                                saveToPictures(context, src, "flux-${job.id}.jpg")
                                Toast.makeText(context, context.getString(R.string.viewer_saved_to_gallery), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                viewerNotice = context.getString(R.string.viewer_save_failed)
                            }
                        }
                    },
                    onDetails = { showDetails = true },
                    onDelete = {
                        showDeleteConfirm = true
                    },
                )
            }
        },
    ) { inner ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            if (jobs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                }
                return@Box
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = zoomedPage == null,
            ) { page ->
                val job = jobs.getOrNull(page) ?: return@HorizontalPager
                val previewModel = repository.previewModel(job.id)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("viewer_page_${job.id}"),
                ) {
                    ZoomableImage(
                        model = previewModel,
                        onClick = { showUI = !showUI },
                        onZoomedChange = { isZoomed ->
                            if (pagerState.currentPage == page) {
                                zoomedPage = if (isZoomed) job.id else null
                            }
                        },
                        shouldReset = pagerState.currentPage != page,
                    )
                }
            }

            // Details Overlay (Modal)
            if (showDetails) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .combinedClickable(
                            onClick = { showDetails = false },
                            onLongClick = {},
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val currentJob = jobs.getOrNull(pagerState.currentPage)
                    if (currentJob != null) {
                        JobDetailsSheet(
                            job = currentJob,
                            prompts = prompts,
                            availability = repository.offlineAvailability(currentJob.id),
                            onClose = { showDetails = false },
                        )
                    }
                }
            }

            // Compare Before/After Overlay
            if (showCompare) {
                val currentJob = jobs.getOrNull(pagerState.currentPage)
                val inputId = currentJob?.input_id
                if (currentJob != null && inputId != null) {
                    CompareOverlay(
                        beforeModel = repository.inputModel(inputId),
                        afterModel = repository.previewModel(currentJob.id),
                        onDismiss = { showCompare = false },
                    )
                } else {
                    showCompare = false
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.viewer_delete_confirm_title)) },
            text = { Text(stringResource(R.string.viewer_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val job = currentJob
                        showDeleteConfirm = false
                        if (job != null) {
                            viewModel.deleteJob(job.id)
                            if (jobs.size <= 1) onBack()
                        }
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ZoomableImage(
    model: Any?,
    onClick: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    shouldReset: Boolean,
) {
    val zoomableState = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 5f))
    val zoomableImageState = rememberZoomableImageState(zoomableState)
    var loadError by remember(model) { mutableStateOf(false) }

    LaunchedEffect(shouldReset) {
        if (shouldReset) {
            zoomableState.resetZoom(snap())
        }
    }

    LaunchedEffect(zoomableState.zoomFraction) {
        val frac = zoomableState.zoomFraction
        onZoomedChange(frac != null && frac > 0.01f)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (loadError) {
            MissingViewerImage()
        } else {
            val context = LocalContext.current
            val request = remember(model) {
                ImageRequest.Builder(context)
                    .data(model)
                    .listener(
                        onSuccess = { _, _ -> loadError = false },
                        onError = { _, _ -> loadError = true },
                    )
                    .build()
            }
            ZoomableAsyncImage(
                model = request,
                contentDescription = null,
                state = zoomableImageState,
                contentScale = ContentScale.Fit,
                onClick = { onClick() },
                modifier = Modifier.fillMaxSize(),
            )
            if (!zoomableImageState.isImageDisplayed && !zoomableImageState.isPlaceholderDisplayed) {
                androidx.compose.material3.CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun MissingViewerImage() {
    MissingImageState(
        modifier = Modifier.fillMaxSize(),
        label = stringResource(R.string.viewer_image_unavailable_offline),
        dark = true,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JobDetailsSheet(
    job: JobSummaryDto,
    prompts: List<PromptDto>,
    availability: OfflineImageAvailability,
    onClose: () -> Unit,
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.4f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier =
            Modifier
                .padding(24.dp),
        ) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                    .combinedClickable(onClick = onClose, onLongClick = {}),
            )

            val promptLabel = resolvePromptLabel(prompts, job.effectivePromptId, job.prompt_text)
            Text(text = promptLabel, style = MaterialTheme.typography.headlineSmall)

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val locale = LocalLocale.current.platformLocale
                DetailRow(
                    stringResource(R.string.viewer_detail_created),
                    SimpleDateFormat("MMM d, yyyy · HH:mm", locale).format(Date(job.created_at * 1000)),
                )
                job.duration_seconds?.let {
                    DetailRow(
                        stringResource(R.string.viewer_detail_duration),
                        stringResource(R.string.viewer_detail_duration_seconds_format, it),
                    )
                }
                DetailRow(
                    stringResource(R.string.viewer_detail_offline),
                    stringResource(availability.toDetailTextRes()),
                )
                DetailRow(stringResource(R.string.viewer_detail_prompt), promptLabel)
                job.prompt_text?.takeIf { it.isNotBlank() }?.let {
                    DetailRow(stringResource(R.string.viewer_detail_prompt_text), it)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(
            text = value,
            style = if (isMonospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun OfflineImageAvailability.toDetailTextRes(): Int = when {
    resultCached -> R.string.viewer_offline_original_cached
    previewCached -> R.string.viewer_offline_preview_cached
    thumbCached -> R.string.viewer_offline_thumb_cached
    else -> R.string.viewer_offline_needs_server
}

@Composable
private fun ViewerActionBar(
    hasInput: Boolean,
    selectingInput: Boolean,
    onCompare: () -> Unit,
    onUseInput: () -> Unit,
    onSave: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    ActionBarSurface {
        if (hasInput) {
            ActionIcon(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                label = stringResource(R.string.viewer_compare),
                onClick = onCompare,
            )
            ActionIcon(
                icon = Icons.Default.Image,
                label = stringResource(if (selectingInput) R.string.viewer_loading else R.string.viewer_use_input),
                onClick = onUseInput,
                enabled = !selectingInput,
            )
        }
        ActionIcon(
            icon = Icons.Default.Download,
            label = stringResource(R.string.viewer_save),
            onClick = onSave,
        )
        ActionIcon(
            icon = Icons.Default.Info,
            label = stringResource(R.string.viewer_details),
            onClick = onDetails,
        )
        ActionIcon(
            icon = Icons.Default.Delete,
            label = stringResource(R.string.viewer_delete),
            onClick = onDelete,
        )
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            )
        }
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
