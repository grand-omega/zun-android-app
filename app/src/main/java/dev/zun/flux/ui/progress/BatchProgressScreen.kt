package dev.zun.flux.ui.progress

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
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import dev.zun.flux.R
import dev.zun.flux.Tuning
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.ui.common.LoadingScrim
import dev.zun.flux.ui.common.PanelShape
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone
import dev.zun.flux.ui.theme.tabular
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BatchProgressScreen(
    jobIds: List<String>,
    jobs: JobRepository,
    images: ImageSourceRepository,
    onViewResult: (String) -> Unit,
    onBack: () -> Unit,
) {
    val deletedJobIds by jobs.deletedJobIds().collectAsStateWithLifecycle(initialValue = emptySet())
    val activeJobIds = jobIds.filterNot { it in deletedJobIds }

    // ListDetailPaneScaffold gives us free phone↔tablet behavior: phones toggle
    // between list (grid) and detail (focused pager); tablets show both panes.
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeJobIds) {
        val focused = navigator.currentDestination?.contentKey
        if (focused != null && focused !in activeJobIds.indices) {
            scope.launch { navigator.navigateBack() }
        }
    }

    LaunchedEffect(activeJobIds.isEmpty()) {
        if (activeJobIds.isEmpty()) onBack()
    }

    if (activeJobIds.isEmpty()) return

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                BatchGrid(
                    jobIds = activeJobIds,
                    jobs = jobs,
                    images = images,
                    onTileClick = { index, isDone ->
                        if (isDone) {
                            onViewResult(activeJobIds[index])
                        } else {
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, index)
                            }
                        }
                    },
                    onBack = onBack,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val focused = navigator.currentDestination?.contentKey
                if (focused != null) {
                    BatchFocused(
                        jobIds = activeJobIds,
                        initialIndex = focused,
                        jobs = jobs,
                        images = images,
                        onViewResult = onViewResult,
                        onBack = { scope.launch { navigator.navigateBack() } },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchGrid(
    jobIds: List<String>,
    jobs: JobRepository,
    images: ImageSourceRepository,
    onTileClick: (index: Int, isDone: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.progress_batch_n_generations_format, jobIds.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                    jobs = jobs,
                    images = images,
                    onClick = { isDone -> onTileClick(index, isDone) },
                )
            }
        }
    }
}

@Composable
private fun BatchTile(
    jobId: String,
    jobs: JobRepository,
    images: ImageSourceRepository,
    onClick: (isDone: Boolean) -> Unit,
) {
    val viewModel: ProgressViewModel = viewModel(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    repository = jobs,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(jobId) { viewModel.start(jobId) }

    val currentDto = (state as? PollState.Running)?.dto ?: (state as? PollState.Done)?.dto
    val inputModel = remember(currentDto?.input_id) { images.inputModel(currentDto?.input_id) }
    val isDone = state is PollState.Done
    val resultModel = remember(jobId, isDone) {
        if (isDone) images.previewModel(jobId) else null
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
                contentDescription = stringResource(R.string.progress_batch_completed_generation),
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
                    contentDescription = stringResource(R.string.progress_batch_done),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            // Dimmed input + state overlay.
            AsyncImage(
                model = inputModel,
                contentDescription = stringResource(R.string.progress_batch_source_image_in_progress),
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
                            text = stringResource(R.string.progress_pct_format, pct),
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
                    description = stringResource(R.string.progress_batch_failed),
                )

                PollState.Deleted -> CornerBadge(
                    color = Color.DarkGray,
                    icon = Icons.Default.Close,
                    description = stringResource(R.string.progress_batch_deleted),
                )

                PollState.Cancelled -> CornerBadge(
                    color = Color.DarkGray,
                    icon = Icons.Default.Close,
                    description = stringResource(R.string.progress_batch_cancelled),
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
    jobs: JobRepository,
    images: ImageSourceRepository,
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
                title = { Text(stringResource(R.string.progress_batch_index_format, pagerState.currentPage + 1, jobIds.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.progress_batch_back_to_overview))
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
                jobs = jobs,
                images = images,
                onViewResult = { onViewResult(jobIds[page]) },
            )
        }
    }
}

@Composable
private fun BatchPage(
    jobId: String,
    jobs: JobRepository,
    images: ImageSourceRepository,
    onViewResult: () -> Unit,
) {
    val viewModel: ProgressViewModel = viewModel(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    repository = jobs,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentDto = (state as? PollState.Running)?.dto ?: (state as? PollState.Done)?.dto
    val inputModel = remember(currentDto?.input_id) { images.inputModel(currentDto?.input_id) }
    val isDone = state is PollState.Done
    val resultModel = remember(jobId, isDone) {
        if (isDone) images.resultModel(jobId) else null
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
                        contentDescription = stringResource(R.string.progress_batch_completed_generation),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = inputModel,
                        contentDescription = stringResource(R.string.progress_batch_source_image_in_progress),
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
                        stringResource(R.string.progress_starting),
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
                                text = stringResource(R.string.progress_pct_format, pct),
                                style = MaterialTheme.typography.displayMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    is PollState.Done -> Text(stringResource(R.string.progress_done), style = MaterialTheme.typography.titleMedium)

                    is PollState.Failed -> {
                        Text(s.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.retry(jobId) }) { Text(stringResource(R.string.common_retry)) }
                    }

                    PollState.Deleted -> Text(
                        text = stringResource(R.string.progress_deleted),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    PollState.Cancelled -> Text(
                        text = stringResource(R.string.progress_cancelled),
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
                    Text(stringResource(R.string.progress_batch_view_result))
                }

                PollState.Starting, is PollState.Running -> OutlinedButton(
                    onClick = { viewModel.cancelJob(jobId) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }

                PollState.Deleted -> Unit

                else -> Unit
            }
        }
    }
}
