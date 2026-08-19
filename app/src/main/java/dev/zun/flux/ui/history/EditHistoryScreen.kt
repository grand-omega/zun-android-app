package dev.zun.flux.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import dev.zun.flux.R
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.ui.common.BackNavigationIcon
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHistoryScreen(
    lineageRootId: String,
    jobs: JobRepository,
    images: ImageSourceRepository,
    onJobClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: EditHistoryViewModel = viewModel(
        key = "edit-history-$lineageRootId",
        factory = viewModelFactory {
            initializer { EditHistoryViewModel(lineageRootId, jobs) }
        },
    )
    val entries by viewModel.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_history_title)) },
                navigationIcon = {
                    BackNavigationIcon(onBack = onBack, contentDescription = stringResource(R.string.common_back))
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            items(entries, key = { it.id }) { entry ->
                EditHistoryRow(
                    entry = entry,
                    thumbModel = images.thumbModel(entry.id),
                    onClick = { onJobClick(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun EditHistoryRow(
    entry: JobSummaryDto,
    thumbModel: Any?,
    onClick: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val formattedDate = remember(entry.created_at, locale) {
        SimpleDateFormat("MMM d, yyyy · HH:mm", locale).format(Date(entry.created_at * 1000))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = thumbModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
