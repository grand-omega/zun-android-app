package dev.zun.flux.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import dev.zun.flux.R
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.ui.common.BackNavigationIcon

/**
 * Settings' "Clear offline cache" flow (feature 009) — replaces the previous blind
 * confirm-dialog with a real preview of exactly what's cached before anything is deleted,
 * per spec FR-007-010.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheCleanupScreen(
    images: ImageSourceRepository,
    onBack: () -> Unit,
) {
    val viewModel: CacheCleanupViewModel = viewModel(
        factory = viewModelFactory { initializer { CacheCleanupViewModel(images) } },
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cache_cleanup_title)) },
                navigationIcon = {
                    BackNavigationIcon(onBack = onBack, contentDescription = stringResource(R.string.common_back))
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.entries.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.cache_cleanup_empty))
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.entries, key = { it.jobId }) { entry ->
                            AsyncImage(
                                model = images.thumbModel(entry.jobId),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.cache_cleanup_summary_format,
                                state.entries.size,
                                state.entries.size,
                                formatBytes(state.entries.sumOf { it.bytes }),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        state.blockedMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = viewModel::confirmClear, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cache_cleanup_confirm))
                        }
                    }
                }
            }
        }
    }
}
