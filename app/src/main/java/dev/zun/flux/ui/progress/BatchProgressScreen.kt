package dev.zun.flux.ui.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import dev.zun.flux.Tuning
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.ui.common.LoadingScrim
import dev.zun.flux.ui.common.PanelShape
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone
import dev.zun.flux.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProgressScreen(
    jobIds: List<String>,
    repository: JobRepository,
    onViewResult: (String) -> Unit,
    onBack: () -> Unit,
) {
    // null = grid overview, otherwise the index focused in the pager.
    var focusedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val deletedJobIds by repository.deletedJobIds().collectAsStateWithLifecycle(initialValue = emptySet())
    val activeJobIds = jobIds.filterNot { it in deletedJobIds }

    LaunchedEffect(activeJobIds, focusedIndex) {
        val index = focusedIndex
        if (index != null && index !in activeJobIds.indices) {
            focusedIndex = activeJobIds.indices.lastOrNull()
        }
    }

    LaunchedEffect(activeJobIds.isEmpty()) {
        if (activeJobIds.isEmpty()) onBack()
    }

    BackHandler(focusedIndex != null) { focusedIndex = null }

    if (activeJobIds.isEmpty()) {
        return
    } else if (focusedIndex == null) {
        BatchGrid(
            jobIds = activeJobIds,
            repository = repository,
            onTileClick = { index, isDone ->
                if (isDone) onViewResult(activeJobIds[index]) else focusedIndex = index
            },
            onBack = onBack,
        )
    } else {
        BatchFocused(
            jobIds = activeJobIds,
            initialIndex = focusedIndex!!,
            repository = repository,
            onViewResult = onViewResult,
            onBack = { focusedIndex = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchGrid(
    jobIds: List<String>,
    repository: JobRepository,
    onTileClick: (index: Int, isDone: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${jobIds.size} generations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            itemsIndexed(jobIds, key = { _, id -> id }) { index, jobId ->
                BatchTile(
                    jobId = jobId,
                    repository = repository,
                    onClick = { isDone -> onTileClick(index, isDone) },
                )
            }
        }
    }
}

@Composable
private fun BatchTile(
    jobId: String,
    repository: JobRepository,
    onClick: (isDone: Boolean) -> Unit,
) {
    val viewModel: ProgressViewModel = viewModel(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    repository = repository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(jobId) { viewModel.start(jobId) }

    val currentDto = (state as? PollState.Running)?.dto ?: (state as? PollState.Done)?.dto
    val inputModel = remember(currentDto?.input_id) { repository.inputModel(currentDto?.input_id) }
    val isDone = state is PollState.Done
    val resultModel = remember(jobId, isDone) {
        if (isDone) repository.previewModel(jobId) else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .clickable { onClick(isDone) },
        contentAlignment = Alignment.Center,
    ) {
        if (isDone && resultModel != null) {
            AsyncImage(
                model = resultModel,
                contentDescription = "Completed generation",
                modifier = Modifier.fillMaxSize(),
            )
            // Done check in corner.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(Color(0xFF1D9E75), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Done",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            // Dimmed input + state overlay.
            AsyncImage(
                model = inputModel,
                contentDescription = "Source image in progress",
                modifier = Modifier.fillMaxSize().alpha(0.45f),
            )
            when (val s = state) {
                PollState.Starting -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )

                is PollState.Running -> {
                    val pct = s.dto.progress?.let { (it * 100).toInt() }
                    if (pct != null) {
                        Text(
                            text = "$pct%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium.tabular(),
                        )
                    } else {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                is PollState.Failed -> CornerBadge(
                    color = MaterialTheme.colorScheme.error,
                    icon = Icons.Default.Close,
                    description = "Failed",
                )

                PollState.Deleted -> CornerBadge(
                    color = Color.DarkGray,
                    icon = Icons.Default.Close,
                    description = "Deleted",
                )

                PollState.Cancelled -> CornerBadge(
                    color = Color.DarkGray,
                    icon = Icons.Default.Close,
                    description = "Cancelled",
                )

                is PollState.Done -> Unit // handled above
            }
        }
    }
}

@Composable
private fun BoxScope.CornerBadge(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(20.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchFocused(
    jobIds: List<String>,
    initialIndex: Int,
    repository: JobRepository,
    onViewResult: (String) -> Unit,
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { jobIds.size },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} of ${jobIds.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to overview")
                    }
                },
            )
        },
    ) { inner ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) { page ->
            BatchPage(
                jobId = jobIds[page],
                repository = repository,
                onViewResult = { onViewResult(jobIds[page]) },
            )
        }
    }
}

@Composable
private fun BatchPage(
    jobId: String,
    repository: JobRepository,
    onViewResult: () -> Unit,
) {
    val viewModel: ProgressViewModel = viewModel(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    repository = repository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentDto = (state as? PollState.Running)?.dto ?: (state as? PollState.Done)?.dto
    val inputModel = remember(currentDto?.input_id) { repository.inputModel(currentDto?.input_id) }
    val isDone = state is PollState.Done
    val resultModel = remember(jobId, isDone) {
        if (isDone) repository.resultModel(jobId) else null
    }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(jobId) { viewModel.start(jobId) }
    LaunchedEffect(isDone) {
        if (isDone) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = Tuning.MAX_CONTENT_WIDTH),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(PanelShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone && resultModel != null) {
                    AsyncImage(
                        model = resultModel,
                        contentDescription = "Completed generation",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = inputModel,
                        contentDescription = "Source image in progress",
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.6f),
                    )
                    if (state is PollState.Starting || state is PollState.Running) {
                        LoadingScrim(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val s = state) {
                    PollState.Starting -> Text(
                        "Starting…",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    is PollState.Running -> {
                        StatusPill(
                            label = s.dto.status.replaceFirstChar { it.uppercase() },
                            tone = StatusTone.Success,
                        )
                        val pct = s.dto.progress?.let { (it * 100).toInt() }
                        if (pct != null) {
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.displayMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    is PollState.Done -> Text("Done", style = MaterialTheme.typography.titleMedium)

                    is PollState.Failed -> {
                        Text("Failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.retry(jobId) }) { Text("Retry") }
                    }

                    PollState.Deleted -> Text(
                        text = "Deleted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    PollState.Cancelled -> Text(
                        text = "Cancelled",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (state) {
                is PollState.Done -> Button(
                    onClick = onViewResult,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("View result")
                }

                PollState.Starting, is PollState.Running -> OutlinedButton(
                    onClick = { viewModel.cancelJob(jobId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }

                PollState.Deleted -> Unit

                else -> Unit
            }
        }
    }
}
