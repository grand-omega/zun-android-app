package dev.zun.flux.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.zun.flux.R
import dev.zun.flux.ui.common.ControlShape

@Composable
internal fun Composer(
    selectedLabel: String?,
    tryHarder: Boolean,
    uploadProgress: Float?,
    batchProgress: BatchProgress?,
    state: SubmitState,
    imageCount: Int,
    canSubmit: Boolean,
    onPromptStripClick: () -> Unit,
    onSubmit: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickRecent: (Int) -> Unit,
    showSourceRow: Boolean,
    showPromptStrip: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showSourceRow) {
            CompactSourceRow(
                imageCount = imageCount,
                onTakePhoto = onTakePhoto,
                onPickGallery = onPickGallery,
                recents = recents,
                isFetchingRecent = isFetchingRecent,
                onPickRecent = onPickRecent,
            )
        }

        if (showPromptStrip) {
            PromptStrip(
                selectedLabel = selectedLabel,
                tryHarder = tryHarder,
                onClick = onPromptStripClick,
            )
        }

        UploadProgressSection(uploadProgress = uploadProgress, batchProgress = batchProgress)

        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state is SubmitState.InFlight -> stringResource(R.string.home_submitting)
                    imageCount > 1 -> stringResource(R.string.home_generate_count_format, imageCount)
                    else -> stringResource(R.string.home_generate)
                },
            )
        }

        if (!canSubmit && state !is SubmitState.InFlight) {
            Text(
                text = when {
                    imageCount == 0 -> stringResource(R.string.home_need_image)
                    selectedLabel == null -> stringResource(R.string.home_need_prompt)
                    else -> stringResource(R.string.home_finish_prompt)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val failure = state as? SubmitState.Failed
        if (failure != null) {
            Text(failure.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PromptStrip(
    selectedLabel: String?,
    tryHarder: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_prompt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = selectedLabel ?: stringResource(R.string.home_choose_a_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedLabel != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tryHarder) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_hq),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.home_open_prompt_picker),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UploadProgressSection(
    uploadProgress: Float?,
    batchProgress: BatchProgress?,
) {
    if (uploadProgress == null && batchProgress == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val label = when {
            batchProgress != null && uploadProgress != null -> stringResource(
                R.string.home_uploading_batch_with_pct_format,
                batchProgress.current,
                batchProgress.total,
                (uploadProgress * 100).toInt(),
            )

            batchProgress != null -> stringResource(
                R.string.home_uploading_batch_format,
                batchProgress.current,
                batchProgress.total,
            )

            uploadProgress != null -> stringResource(
                R.string.home_uploading_pct_format,
                (uploadProgress * 100).toInt(),
            )

            else -> ""
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (uploadProgress != null) {
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
