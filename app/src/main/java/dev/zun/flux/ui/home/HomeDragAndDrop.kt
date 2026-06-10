package dev.zun.flux.ui.home

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent

/**
 * Accepts images dragged from other apps (split-screen Gallery, Files, DeX
 * windows) and hands their content URIs to [onImagesDropped]. Apply the
 * returned modifier to the drop surface.
 */
@Composable
fun rememberImageDropTarget(onImagesDropped: (List<Uri>) -> Unit): Modifier {
    val activity = LocalActivity.current
    val target = remember(activity, onImagesDropped) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dragEvent = event.toAndroidDragEvent()
                // Keep read access to the dragged content URIs while they're
                // copied into our cache; released with the activity.
                activity?.requestDragAndDropPermissions(dragEvent)
                val clipData = dragEvent.clipData ?: return false
                val uris = (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }
                if (uris.isEmpty()) return false
                onImagesDropped(uris)
                return true
            }
        }
    }
    return Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.mimeTypes().any { it.startsWith("image/") }
        },
        target = target,
    )
}
