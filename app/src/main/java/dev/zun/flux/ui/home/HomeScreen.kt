package dev.zun.flux.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import dev.zun.flux.BuildConfig
import dev.zun.flux.R
import dev.zun.flux.data.repo.JobRepository

private const val HARDCODED_PROMPT_ID = "ghibli"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: JobRepository,
    onJobSubmitted: (String) -> Unit,
) {
    val viewModel: HomeViewModel =
        viewModel(
            factory =
            viewModelFactory {
                initializer { HomeViewModel(repository) }
            },
        )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) imageUri = uri
        }

    LaunchedEffect(state) {
        val s = state
        if (s is SubmitState.Done) {
            viewModel.acknowledgeDone()
            onJobSubmitted(s.jobId)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("FluxEdit") }) },
    ) { inner ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Image source")
            OutlinedButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("From gallery")
            }

            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = {
                        imageUri = Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/${R.raw.sample}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use sample image (debug)")
                }
            }

            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                val uri = imageUri
                if (uri == null) {
                    Text("No image selected")
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text("Prompt")
            Text("Ghibli style (hardcoded for M2)")

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val uri = imageUri ?: return@Button
                    viewModel.submit(uri, HARDCODED_PROMPT_ID)
                },
                enabled = imageUri != null && state !is SubmitState.InFlight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state is SubmitState.InFlight) "Submitting…" else "Generate")
            }

            val failure = state as? SubmitState.Failed
            if (failure != null) {
                Text("Submit failed: ${failure.message}")
            }
        }
    }
}
