package dev.zun.flux.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.zun.flux.R
import dev.zun.flux.data.api.PromptDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibrarySheet(
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    tryHarder: Boolean,
    onTryHarderChange: (Boolean) -> Unit,
    onSavePromptClick: () -> Unit,
    onManagePrompts: () -> Unit,
    onSelectPrompt: (Long) -> Unit,
    onDismiss: () -> Unit,
    pinnedIds: Set<Long> = emptySet(),
    onTogglePin: (Long) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PromptLibraryContent(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            customPromptText = customPromptText,
            onCustomPromptChange = onCustomPromptChange,
            tryHarder = tryHarder,
            onTryHarderChange = onTryHarderChange,
            onSavePromptClick = onSavePromptClick,
            onManagePrompts = onManagePrompts,
            onSelectPrompt = onSelectPrompt,
            pinnedIds = pinnedIds,
            onTogglePin = onTogglePin,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        )
    }
}

/**
 * The prompt library body, shared between the modal sheet (phone/folded) and
 * the embedded wide-screen pane. [fillHeight] lets the prompt list grow to
 * fill the pane instead of capping at sheet height.
 */
@Composable
fun PromptLibraryContent(
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    tryHarder: Boolean,
    onTryHarderChange: (Boolean) -> Unit,
    onSavePromptClick: () -> Unit,
    onManagePrompts: () -> Unit,
    onSelectPrompt: (Long) -> Unit,
    modifier: Modifier = Modifier,
    pinnedIds: Set<Long> = emptySet(),
    onTogglePin: (Long) -> Unit = {},
    fillHeight: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val builtIns = prompts.filter { it.id != CUSTOM_PROMPT_ID }
    val matched = remember(query, builtIns) {
        if (query.isBlank()) {
            builtIns
        } else {
            builtIns.filter { it.label.contains(query.trim(), ignoreCase = true) }
        }
    }
    // Pinned first, original order preserved within each group.
    val ordered = remember(matched, pinnedIds) {
        val (pinned, rest) = matched.partition { it.id in pinnedIds }
        pinned + rest
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.prompts_choose),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onManagePrompts) { Text(stringResource(R.string.prompts_manage)) }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.prompts_high_quality),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.prompts_high_quality_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Switch(checked = tryHarder, onCheckedChange = onTryHarderChange)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.prompts_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        CustomPromptItem(
            expanded = selectedPromptId == CUSTOM_PROMPT_ID,
            customText = customPromptText,
            onClick = { onSelectPrompt(CUSTOM_PROMPT_ID) },
            onTextChange = onCustomPromptChange,
            onSaveClick = onSavePromptClick,
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier.heightIn(max = 360.dp)),
        ) {
            items(ordered, key = { it.id }) { prompt ->
                PromptRow(
                    label = prompt.label,
                    description = prompt.description,
                    selected = selectedPromptId == prompt.id,
                    pinned = prompt.id in pinnedIds,
                    onClick = { onSelectPrompt(prompt.id) },
                    onTogglePin = { onTogglePin(prompt.id) },
                )
            }
            if (ordered.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.prompts_no_match_format, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptRow(
    label: String,
    description: String?,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(if (pinned) R.string.prompts_unpin else R.string.prompts_pin),
                    tint = if (pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptManageSheet(
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    onDeletePrompt: (Long) -> Unit,
    onUpdatePrompt: (Long, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val savedPrompts = prompts.filter { it.id != CUSTOM_PROMPT_ID }
    var editingPrompt by remember { mutableStateOf<PromptDto?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.prompts_manage_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
            }

            if (savedPrompts.isEmpty()) {
                Text(
                    text = stringResource(R.string.prompts_no_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(savedPrompts, key = { it.id }) { prompt ->
                        PromptManageRow(
                            label = prompt.label,
                            description = prompt.description,
                            selected = selectedPromptId == prompt.id,
                            onEdit = { editingPrompt = prompt },
                            onDelete = { onDeletePrompt(prompt.id) },
                        )
                    }
                }
            }
        }
    }

    editingPrompt?.let { prompt ->
        PromptEditDialog(
            prompt = prompt,
            onSave = { label, text ->
                onUpdatePrompt(prompt.id, label, text)
                editingPrompt = null
            },
            onDismiss = { editingPrompt = null },
        )
    }
}

@Composable
private fun PromptEditDialog(
    prompt: PromptDto,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(prompt.label) }
    var text by remember { mutableStateOf(prompt.text.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prompts_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.prompts_edit_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.prompts_edit_text)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && text.isNotBlank(),
                onClick = { onSave(label, text) },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun PromptManageRow(
    label: String,
    description: String?,
    selected: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.prompts_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.prompts_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CustomPromptItem(
    expanded: Boolean,
    customText: String,
    onClick: () -> Unit,
    onTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (expanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (expanded) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.prompts_write_your_own),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (expanded) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (expanded) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(R.string.prompts_custom_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = customText.isNotBlank(),
                    onClick = onSaveClick,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.prompts_save_prompt))
                }
            }
        }
    }
}
