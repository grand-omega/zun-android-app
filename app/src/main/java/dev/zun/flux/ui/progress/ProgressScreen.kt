package dev.zun.flux.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import dev.zun.flux.Tuning
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.ui.common.LoadingScrim
import dev.zun.flux.ui.common.PanelShape
import dev.zun.flux.ui.common.StatusPill
import dev.zun.flux.ui.common.StatusTone
import dev.zun.flux.ui.theme.tabular
import dev.zun.flux.util.resolvePromptLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    jobId: String,
    repository: JobRepository,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ProgressViewModel =
        viewModel(
            key = jobId,
            factory =
            viewModelFactory {
                initializer {
                    ProgressViewModel(
                        repository = repository,
                    )
                }
            },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prompts by repository.promptsState.collectAsStateWithLifecycle()
    val currentDto = (state as? PollState.Running)?.dto ?: (state as? PollState.Done)?.dto
    val inputModel = remember(currentDto?.input_id) { repository.inputModel(currentDto?.input_id) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(jobId) { viewModel.start(jobId) }

    LaunchedEffect(state) {
        if (state is PollState.Done) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generating") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = Tuning.MAX_CONTENT_WIDTH),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Dimmed input preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(PanelShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = inputModel,
                        contentDescription = "Source image being generated",
                        modifier = Modifier.fillMaxSize().alpha(0.6f),
                    )
                    LoadingScrim(modifier = Modifier.fillMaxSize())
                }

                // Status info
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val s = state) {
                        PollState.Starting -> Text("Starting…", style = MaterialTheme.typography.titleMedium)

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
                            Button(onClick = { viewModel.retry(jobId) }) {
                                Text("Retry")
                            }
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

                // Metadata card
                (state as? PollState.Running)?.dto?.let { dto ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MetadataRow("Prompt", resolvePromptLabel(prompts, dto.effectivePromptId, dto.prompt_text))
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = "You can go back and pull-refresh Gallery later to pick up completed generations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )

                OutlinedButton(
                    onClick = {
                        viewModel.cancelJob(jobId)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(
            text = value,
            style =
            if (isMonospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}
