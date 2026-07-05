package dev.zun.flux.ui.home

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zun.flux.R
import dev.zun.flux.data.repo.PriorEditsInfo
import dev.zun.flux.ui.common.PanelShape

const val MAX_BATCH_IMAGES = 20

@Composable
fun ImageHero(
    imageUris: List<Uri>,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickRecent: (Int) -> Unit,
    onRemove: (Uri) -> Unit,
    priorEditsByUri: Map<Uri, PriorEditsInfo> = emptyMap(),
) {
    when {
        imageUris.isEmpty() -> EmptyHero(
            recents = recents,
            isFetchingRecent = isFetchingRecent,
            onPickGallery = onPickGallery,
            onTakePhoto = onTakePhoto,
            onPickRecent = onPickRecent,
        )

        imageUris.size == 1 -> Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(PanelShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUris[0],
                contentDescription = stringResource(R.string.home_selected_source_image),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            priorEditsByUri[imageUris[0]]?.let { info ->
                PriorEditsBadge(info, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }

        else -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.home_n_images_same_prompt_format, imageUris.size, imageUris.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(imageUris, key = { it.toString() }) { uri ->
                    ImageThumb(uri = uri, onRemove = { onRemove(uri) }, priorEdits = priorEditsByUri[uri])
                }
                if (imageUris.size < MAX_BATCH_IMAGES) {
                    item(key = "__add_more__") {
                        AddMoreTile(onClick = onPickGallery)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PanelShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageUris[0],
                    contentDescription = stringResource(R.string.home_primary_selected_source_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun EmptyHero(
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickRecent: (Int) -> Unit,
) {
    Surface(
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onPickGallery),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(
                text = stringResource(R.string.home_add_source_image),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.heightIn(min = 4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                IconLabel(
                    icon = Icons.Default.PhotoCamera,
                    label = stringResource(R.string.home_take_photo),
                    onClick = onTakePhoto,
                )
                IconLabel(
                    icon = Icons.Default.Image,
                    label = stringResource(R.string.home_from_gallery),
                    onClick = onPickGallery,
                )
            }
            RecentInputRow(
                recents = recents,
                isFetchingRecent = isFetchingRecent,
                onPickRecent = onPickRecent,
            )
        }
    }
}

@Composable
private fun RecentInputRow(
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickRecent: (Int) -> Unit,
) {
    if (recents.isEmpty()) return

    Spacer(Modifier.heightIn(min = 16.dp))
    Text(
        text = stringResource(R.string.home_recent),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
    Spacer(Modifier.heightIn(min = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        recents.forEach { (id, model, selected) ->
            Box(modifier = Modifier.size(64.dp)) {
                RecentInputThumbnail(
                    model = model,
                    selected = selected,
                    enabled = !isFetchingRecent,
                    size = 64,
                    cornerRadius = 8,
                    onClick = { onPickRecent(id) },
                )
            }
        }
    }
}

@Composable
private fun IconLabel(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun CompactSourceRow(
    imageCount: Int,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    recents: List<Triple<Int, Any?, Boolean>>,
    isFetchingRecent: Boolean,
    onPickRecent: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onTakePhoto) {
            Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.home_take_photo))
        }
        IconButton(onClick = onPickGallery) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.home_add_image))
        }
        Spacer(Modifier.width(4.dp))
        recents.take(3).forEach { (id, model, selected) ->
            Box(modifier = Modifier.size(36.dp)) {
                RecentInputThumbnail(
                    model = model,
                    selected = selected,
                    enabled = !isFetchingRecent,
                    size = 36,
                    cornerRadius = 6,
                    onClick = { onPickRecent(id) },
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.weight(1f))
        if (imageCount > 1) {
            Text(
                text = pluralStringResource(R.plurals.home_n_selected_format, imageCount, imageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun RecentInputThumbnail(
    model: Any?,
    selected: Boolean,
    enabled: Boolean,
    size: Int,
    cornerRadius: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    AsyncImage(
        model = model,
        contentDescription = stringResource(
            if (selected) R.string.home_selected_recent_source_image else R.string.home_recent_source_image,
        ),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {},
            ),
    )
    if (selected) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(shape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            if (size >= 48) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.home_already_selected),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ImageThumb(uri: Uri, onRemove: () -> Unit, priorEdits: PriorEditsInfo? = null) {
    Box(modifier = Modifier.size(96.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.home_selected_source_image_thumbnail),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                .combinedClickable(onClick = onRemove, onLongClick = {}),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.home_remove_image),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        if (priorEdits != null) {
            PriorEditsBadge(priorEdits, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
        }
    }
}

/** Small "you've edited this photo before" indicator (User Story 1). Never blocks submission. */
@Composable
private fun PriorEditsBadge(info: PriorEditsInfo, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
    ) {
        Text(
            text = pluralStringResource(R.plurals.home_edited_before_format, info.editCount, info.editCount),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddMoreTile(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .size(96.dp)
            .combinedClickable(onClick = onClick, onLongClick = {}),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.home_add_more_images),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_add),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
