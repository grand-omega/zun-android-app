package dev.zun.flux.ui.gallery

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.util.saveToPictures
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    initialJobId: String,
    viewModel: GalleryViewModel,
    repository: JobRepository,
    onBack: () -> Unit,
) {
    val jobs by viewModel.jobs.collectAsState()
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
    var showContextMenu by remember { mutableStateOf(false) }
    var showUI by remember { mutableStateOf(true) }

    // This state tells the pager if it can scroll.
    // It is updated by the current visible page's zoom level.
    var isPagerScrollEnabled by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Black,
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
                userScrollEnabled = isPagerScrollEnabled,
            ) { page ->
                val job = jobs.getOrNull(page) ?: return@HorizontalPager
                val model = repository.resultModel(job.id)

                ZoomableImage(
                    model = model,
                    onClick = { showUI = !showUI },
                    onLongClick = { showContextMenu = true },
                    onZoomStateChanged = { isZoomed ->
                        // Only the current page should control the pager scroll
                        if (pagerState.currentPage == page) {
                            isPagerScrollEnabled = !isZoomed
                        }
                    },
                    shouldReset = pagerState.currentPage != page,
                )

                // Context Menu for Long Press
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("View details") },
                        onClick = {
                            showContextMenu = false
                            showDetails = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Save to photos") },
                        onClick = {
                            showContextMenu = false
                            scope.launch {
                                try {
                                    saveToPictures(context, model ?: return@launch, "flux-${job.id}.jpg")
                                    Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showContextMenu = false
                            viewModel.deleteJob(job.id)
                            if (jobs.size <= 1) onBack()
                        },
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
                            onClose = { showDetails = false },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(
    model: Any?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit,
    shouldReset: Boolean,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset when page is hidden
    LaunchedEffect(shouldReset) {
        if (shouldReset) {
            scale = 1f
            offset = Offset.Zero
            onZoomStateChanged(false)
        }
    }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomStateChanged(false)
                    } else {
                        scale = 3f
                        onZoomStateChanged(true)
                    }
                },
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    if (newScale != scale) {
                        scale = newScale
                        onZoomStateChanged(newScale > 1f)
                    }

                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
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
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JobDetailsSheet(
    job: JobSummaryDto,
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

            Text(
                text = job.prompt_label ?: job.prompt_id,
                style = MaterialTheme.typography.headlineSmall,
            )

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailRow("Job ID", job.id, isMonospace = true)
                DetailRow(
                    "Created",
                    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(job.created_at * 1000)),
                )
                job.duration_seconds?.let {
                    DetailRow("Duration", "${it}s")
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
