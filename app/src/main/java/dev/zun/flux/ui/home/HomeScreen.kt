package dev.zun.flux.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.JobRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: JobRepository,
    windowSizeClass: WindowSizeClass,
    capturedUri: Uri? = null,
    onTakePhoto: () -> Unit,
    onGalleryClick: () -> Unit,
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
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var selectedPromptId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(capturedUri) {
        if (capturedUri != null) imageUri = capturedUri
    }

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

    val isWide = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FluxEdit") },
                actions = {
                    IconButton(onClick = onGalleryClick) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Gallery")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        if (isWide) {
            WideHomeContent(
                modifier = Modifier.padding(inner),
                imageUri = imageUri,
                prompts = prompts,
                selectedPromptId = selectedPromptId,
                state = state,
                health = health,
                onTakePhoto = onTakePhoto,
                onPickGallery = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onSelectPrompt = { selectedPromptId = it },
                onSubmit = {
                    val uri = imageUri ?: return@WideHomeContent
                    val promptId = selectedPromptId ?: return@WideHomeContent
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.submit(uri, promptId)
                },
            )
        } else {
            CompactHomeContent(
                modifier = Modifier.padding(inner),
                imageUri = imageUri,
                prompts = prompts,
                selectedPromptId = selectedPromptId,
                state = state,
                health = health,
                onTakePhoto = onTakePhoto,
                onPickGallery = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onSelectPrompt = { selectedPromptId = it },
                onSubmit = {
                    val uri = imageUri ?: return@CompactHomeContent
                    val promptId = selectedPromptId ?: return@CompactHomeContent
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.submit(uri, promptId)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CompactHomeContent(
    modifier: Modifier = Modifier,
    imageUri: Uri?,
    prompts: List<PromptDto>,
    selectedPromptId: String?,
    state: SubmitState,
    health: HealthState,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onSelectPrompt: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Image source", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onTakePhoto,
                modifier = Modifier.weight(1f),
            ) {
                Text("Take photo")
            }
            OutlinedButton(
                onClick = onPickGallery,
                modifier = Modifier.weight(1f),
            ) {
                Text("From gallery")
            }
        }

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUri == null) {
                Text("No image selected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            } else {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Text("Prompt", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            prompts.forEach { prompt ->
                FilterChip(
                    selected = selectedPromptId == prompt.id,
                    onClick = { onSelectPrompt(prompt.id) },
                    label = { Text(prompt.label) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        ConnectionIndicator(health)

        Button(
            onClick = onSubmit,
            enabled = imageUri != null && selectedPromptId != null && state !is SubmitState.InFlight,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state is SubmitState.InFlight) "Submitting…" else "Generate")
        }

        val failure = state as? SubmitState.Failed
        if (failure != null) {
            Text("Submit failed: ${failure.message}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WideHomeContent(
    modifier: Modifier = Modifier,
    imageUri: Uri?,
    prompts: List<PromptDto>,
    selectedPromptId: String?,
    state: SubmitState,
    health: HealthState,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onSelectPrompt: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier =
        modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Image source", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                    Text("Take photo")
                }
                OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f)) {
                    Text("From gallery")
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUri == null) {
                    Text("No image selected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                } else {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Prompt", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                prompts.forEach { prompt ->
                    FilterChip(
                        selected = selectedPromptId == prompt.id,
                        onClick = { onSelectPrompt(prompt.id) },
                        label = { Text(prompt.label) },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            ConnectionIndicator(health)

            Button(
                onClick = onSubmit,
                enabled = imageUri != null && selectedPromptId != null && state !is SubmitState.InFlight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state is SubmitState.InFlight) "Submitting…" else "Generate")
            }

            val failure = state as? SubmitState.Failed
            if (failure != null) {
                Text("Submit failed: ${failure.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConnectionIndicator(health: HealthState) {
    val (color, text) = when (health) {
        HealthState.Checking -> MaterialTheme.colorScheme.outlineVariant to "Checking connection…"
        HealthState.Connected -> Color(0xFF1D9E75) to "Connected to tailnet"
        HealthState.Disconnected -> MaterialTheme.colorScheme.error to "Disconnected from server"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
