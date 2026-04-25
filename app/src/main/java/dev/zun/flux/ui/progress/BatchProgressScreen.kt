package dev.zun.flux.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import dev.zun.flux.data.repo.JobRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchProgressScreen(
    jobIds: List<String>,
    repository: JobRepository,
    onViewResult: (String) -> Unit,
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { jobIds.size })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} of ${jobIds.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
    val context = LocalContext.current
    val viewModel: ProgressViewModel = viewModel(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    repository = repository,
                    workManager = WorkManager.getInstance(context),
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
            modifier = Modifier.widthIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone && resultModel != null) {
                    AsyncImage(
                        model = resultModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = inputModel,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.6f),
                    )
                    if (state is PollState.Starting || state is PollState.Running) {
                        CircularProgressIndicator(color = Color.White)
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
                        AssistChip(
                            onClick = {},
                            label = { Text(s.dto.status.replaceFirstChar { it.uppercase() }) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF1D9E75), CircleShape),
                                )
                            },
                        )
                        val pct = s.dto.progress?.let { (it * 100).toInt() }
                        if (pct != null) {
                            Text(
                                "$pct%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    is PollState.Done -> Text("Done", style = MaterialTheme.typography.titleMedium)
                    is PollState.Failed -> {
                        Text("Failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.retry(jobId) }) { Text("Retry") }
                    }
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
                else -> Unit
            }
        }
    }
}
