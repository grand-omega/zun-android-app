package dev.zun.flux.ui.result

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.zun.flux.data.repo.JobRepository
import androidx.compose.material.icons.filled.Share
import dev.zun.flux.util.saveToPictures
import dev.zun.flux.util.shareImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    jobId: String,
    repository: JobRepository,
    windowSizeClass: WindowSizeClass,
    onTryAnotherPrompt: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inputModel = remember(jobId) { repository.inputModel(jobId) }
    val resultModel = remember(jobId) { repository.resultModel(jobId) }

    var saving by remember { mutableStateOf(false) }
    val isWide = windowSizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Result") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isWide) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CompareImage(
                        model = inputModel,
                        label = "Before",
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    CompareImage(
                        model = resultModel,
                        label = "After",
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { 2 })
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { page ->
                        if (page == 0) {
                            CompareImage(model = inputModel, label = "Before")
                        } else {
                            CompareImage(model = resultModel, label = "After")
                        }
                    }

                    // Pager indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(2) { index ->
                            val color =
                                if (pagerState.currentPage == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape),
                            )
                        }
                    }
                    Text(
                        text = "Swipe to compare",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Text("Job id: $jobId", style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = {
                    val src = resultModel as? Uri ?: return@Button
                    saving = true
                    scope.launch {
                        val msg =
                            try {
                                saveToPictures(context, src, "flux-$jobId.jpg")
                                "Saved to Pictures/FluxEdit"
                            } catch (t: Throwable) {
                                "Save failed: ${t.message}"
                            }
                        saving = false
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = resultModel is Uri && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save to gallery")
            }

            OutlinedButton(
                onClick = {
                    val src = resultModel as? Uri ?: return@OutlinedButton
                    shareImage(context, src)
                },
                enabled = resultModel is Uri,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("Share")
            }

            OutlinedButton(
                onClick = {
                    val src = inputModel as? Uri ?: return@OutlinedButton
                    onTryAnotherPrompt(src)
                },
                enabled = inputModel is Uri,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("Try another prompt on this image")
            }
        }
    }
}

@Composable
private fun CompareImage(
    model: Any?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
