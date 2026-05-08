package dev.zun.flux.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.zun.flux.R
import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.util.resolvePromptLabel
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun DeleteResultDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.result_delete_confirm_title)) },
        text = { Text(stringResource(R.string.result_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
internal fun SavePromptDialog(
    customPromptText: String,
    label: String,
    onLabelChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.result_save_prompt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = customPromptText.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = onLabelChange,
                    label = { Text(stringResource(R.string.result_save_prompt_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && customPromptText.isNotBlank(),
                onClick = onSave,
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
internal fun ResultDetailsDialog(
    jobDto: JobStatusDto?,
    prompts: List<PromptDto>,
    onDismiss: () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.result_details_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (jobDto != null) {
                    Text(
                        stringResource(
                            R.string.result_details_prompt_format,
                            resolvePromptLabel(prompts, jobDto.effectivePromptId, jobDto.prompt_text),
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.result_details_created_format,
                            SimpleDateFormat("MMM d, yyyy · HH:mm", locale).format(Date(jobDto.created_at * 1000)),
                        ),
                    )
                    val started = jobDto.started_at
                    val completed = jobDto.completed_at
                    if (started != null && completed != null) {
                        Text(stringResource(R.string.result_details_duration_format, completed - started))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}
