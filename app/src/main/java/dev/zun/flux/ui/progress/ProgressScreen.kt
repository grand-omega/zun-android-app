package dev.zun.flux.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zun.flux.data.repo.JobRepository

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
                initializer { ProgressViewModel(repository) }
            },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(jobId) { viewModel.start(jobId) }

    LaunchedEffect(state) {
        if (state is PollState.Done) onDone()
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
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()

            when (val s = state) {
                PollState.Starting -> Text("Starting…")
                is PollState.Running -> {
                    Text("Status: ${s.dto.status}")
                    val pct = s.dto.progress?.let { (it * 100).toInt() }
                    if (pct != null) Text("$pct%")
                }
                is PollState.Done -> Text("Done")
                is PollState.Failed -> Text("Failed: ${s.message}")
            }

            Text("Job id: $jobId", fontFamily = FontFamily.Monospace)
        }
    }
}
