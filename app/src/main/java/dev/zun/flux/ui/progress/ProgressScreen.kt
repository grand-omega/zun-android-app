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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.work.WorkManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import dev.zun.flux.data.repo.JobRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    jobId: String,
    repository: JobRepository,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ProgressViewModel =
        viewModel(
            key = jobId,
            factory =
            viewModelFactory {
                initializer {
                    ProgressViewModel(
                        repository = repository,
                        workManager = WorkManager.getInstance(context),
                    )
                }
            },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inputModel = remember(jobId) { repository.inputModel(jobId) }
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
                modifier = Modifier.widthIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Dimmed input preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = inputModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(0.6f),
                    )
                    CircularProgressIndicator(color = Color.White)
                }

                // Status info
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val s = state) {
                        PollState.Starting -> Text("Starting…", style = MaterialTheme.typography.titleMedium)
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
                                Text("$pct%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        is PollState.Done -> Text("Done", style = MaterialTheme.typography.titleMedium)
                        is PollState.Failed -> {
                            Text("Failed: ${s.message}", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.retry(jobId) }) {
                                Text("Retry")
                            }
                        }
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
                            MetadataRow("Prompt", dto.prompt_label ?: dto.prompt_id)
                            MetadataRow("Job ID", dto.id, isMonospace = true)
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

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
