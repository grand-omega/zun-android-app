package dev.zun.flux.ui.gallery

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.ImageNotSupported
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.OfflineImageAvailability
import dev.zun.flux.ui.common.ActionBarSurface
import dev.zun.flux.ui.common.MissingImageState
import dev.zun.flux.util.resolvePromptLabel
import dev.zun.flux.util.saveToPictures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val initialIndex =
        remember(initialJobId, jobs) {
            jobs.indexOfFirst { it.id == initialJobId }.coerceAtLeast(0)
        }

    val pagerState =
        rememberPagerState(
            initialPage = initialIndex,
            pageCount = { jobs.size },
        )

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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                                    "Couldn't load original input: ${t.message}",
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
                                Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                viewerNotice = "Save failed. Connect to the server for uncached originals."
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                userScrollEnabled = zoomedPage == null,
            ) { page ->
                val job = jobs.getOrNull(page) ?: return@HorizontalPager
                val previewModel = repository.previewModel(job.id)

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
            title = { Text("Delete generation?") },
            text = { Text("This removes the generation from FluxEdit history. You can undo from the snackbar.") },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(
    model: Any?,
    onClick: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    shouldReset: Boolean,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    fun clampOffset(
        candidate: Offset,
        currentScale: Float,
    ): Offset {
        if (currentScale <= 1f) return Offset.Zero
        val maxX = (viewportSize.width * (currentScale - 1f)) / 2f
        val maxY = (viewportSize.height * (currentScale - 1f)) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    LaunchedEffect(shouldReset) {
        if (shouldReset) {
            scale = 1f
            offset = Offset.Zero
            onZoomedChange(false)
        }
    }

    LaunchedEffect(scale) {
        onZoomedChange(scale > 1.01f)
    }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewportSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                offset = clampOffset(offset, scale)
            }
            .pointerInput(viewportSize, scale) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { tap ->
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val targetScale = 2.5f
                            val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                            scale = targetScale
                            offset = clampOffset((center - tap) * (targetScale - 1f), targetScale)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val shouldHandle = pressedCount >= 2 || scale > 1.01f
                        if (shouldHandle) {
                            val nextScale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            val nextOffset = if (nextScale > 1.01f) {
                                offset + event.calculatePan()
                            } else {
                                Offset.Zero
                            }
                            scale = nextScale
                            offset = clampOffset(nextOffset, nextScale)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
            loading = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                }
            },
            error = {
                MissingViewerImage()
            },
            success = {
                SubcomposeAsyncImageContent()
            },
        )
    }
}

@Composable
private fun MissingViewerImage() {
    MissingImageState(
        modifier = Modifier.fillMaxSize(),
        label = "Image unavailable offline",
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
                    "Created",
                    SimpleDateFormat("MMM d, yyyy · HH:mm", locale).format(Date(job.created_at * 1000)),
                )
                job.duration_seconds?.let {
                    DetailRow("Duration", "${it}s")
                }
                DetailRow("Offline", availability.toDetailText())
                DetailRow("Prompt", promptLabel)
                job.prompt_text?.takeIf { it.isNotBlank() }?.let {
                    DetailRow("Prompt text", it)
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

private fun OfflineImageAvailability.toDetailText(): String = when {
    resultCached -> "Original cached"
    previewCached -> "Preview cached"
    thumbCached -> "Thumbnail cached"
    else -> "Needs server"
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
                label = "Compare",
                onClick = onCompare,
            )
            ActionIcon(
                icon = Icons.Default.Image,
                label = if (selectingInput) "Loading" else "Use input",
                onClick = onUseInput,
                enabled = !selectingInput,
            )
        }
        ActionIcon(
            icon = Icons.Default.Download,
            label = "Save",
            onClick = onSave,
        )
        ActionIcon(
            icon = Icons.Default.Info,
            label = "Details",
            onClick = onDetails,
        )
        ActionIcon(
            icon = Icons.Default.Delete,
            label = "Delete",
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
