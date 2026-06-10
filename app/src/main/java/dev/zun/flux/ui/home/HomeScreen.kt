package dev.zun.flux.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.zun.flux.data.api.PromptDto

/**
 * Stateless presentation for the home screen. All state comes in via parameters
 * and changes leave via callbacks. Driven by [HomeRoute].
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isWide: Boolean,
    imageUris: List<Uri>,
    prompts: List<PromptDto>,
    selectedPromptId: Long?,
    customPromptText: String,
    onCustomPromptChange: (String) -> Unit,
    tryHarder: Boolean,
    onTryHarderChange: (Boolean) -> Unit,
    onDeletePrompt: (Long) -> Unit,
    onUpdatePrompt: (Long, String, String) -> Unit,
    onSavePromptClick: () -> Unit,
    state: SubmitState,
    uploadProgress: Float?,
    batchProgress: BatchProgress?,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onSelectPrompt: (Long) -> Unit,
    onSubmit: () -> Unit,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickRecent: (Int) -> Unit,
    pinnedIds: Set<Long>,
    onTogglePin: (Long) -> Unit,
    onImagesDropped: (List<Uri>) -> Unit = {},
) {
    var showPromptSheet by rememberSaveable { mutableStateOf(false) }
    var showPromptManageSheet by rememberSaveable { mutableStateOf(false) }
    val dropTargetModifier = rememberImageDropTarget(onImagesDropped)

    val canSubmit = imageUris.isNotEmpty() &&
        selectedPromptId != null &&
        (selectedPromptId != CUSTOM_PROMPT_ID || customPromptText.isNotBlank()) &&
        state !is SubmitState.InFlight

    val selectedLabel = remember(prompts, selectedPromptId, customPromptText) {
        when (val id = selectedPromptId) {
            null -> null

            CUSTOM_PROMPT_ID -> customPromptText.trim().takeIf { it.isNotBlank() }?.let {
                if (it.length <= 40) it else it.take(37) + "…"
            }

            else -> prompts.firstOrNull { it.id == id }?.label
        }
    }

    val composer: @Composable () -> Unit = {
        Composer(
            selectedLabel = selectedLabel,
            tryHarder = tryHarder,
            uploadProgress = uploadProgress,
            batchProgress = batchProgress,
            state = state,
            imageCount = imageUris.size,
            canSubmit = canSubmit,
            onPromptStripClick = { showPromptSheet = true },
            onSubmit = onSubmit,
            onTakePhoto = onTakePhoto,
            onPickGallery = onPickGallery,
            recents = recents,
            isFetchingRecent = isFetchingRecent,
            onPickRecent = onPickRecent,
            showSourceRow = imageUris.isNotEmpty(),
        )
    }

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxSize().then(dropTargetModifier)) {
                ImageHero(
                    imageUris = imageUris,
                    recents = recents,
                    isFetchingRecent = isFetchingRecent,
                    onPickGallery = onPickGallery,
                    onTakePhoto = onTakePhoto,
                    onPickRecent = onPickRecent,
                    onRemove = onRemoveImage,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                composer()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().then(dropTargetModifier)) {
                ImageHero(
                    imageUris = imageUris,
                    recents = recents,
                    isFetchingRecent = isFetchingRecent,
                    onPickGallery = onPickGallery,
                    onTakePhoto = onTakePhoto,
                    onPickRecent = onPickRecent,
                    onRemove = onRemoveImage,
                )
            }
            composer()
        }
    }

    if (showPromptSheet) {
        PromptLibrarySheet(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            customPromptText = customPromptText,
            onCustomPromptChange = onCustomPromptChange,
            tryHarder = tryHarder,
            onTryHarderChange = onTryHarderChange,
            onSavePromptClick = onSavePromptClick,
            onManagePrompts = {
                showPromptSheet = false
                showPromptManageSheet = true
            },
            onSelectPrompt = { id ->
                onSelectPrompt(id)
                if (id != CUSTOM_PROMPT_ID) showPromptSheet = false
            },
            onDismiss = { showPromptSheet = false },
            pinnedIds = pinnedIds,
            onTogglePin = onTogglePin,
        )
    }

    if (showPromptManageSheet) {
        PromptManageSheet(
            prompts = prompts,
            selectedPromptId = selectedPromptId,
            onDeletePrompt = onDeletePrompt,
            onUpdatePrompt = onUpdatePrompt,
            onDismiss = { showPromptManageSheet = false },
        )
    }
}
