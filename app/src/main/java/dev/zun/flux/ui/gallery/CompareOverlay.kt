package dev.zun.flux.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun CompareOverlay(
    beforeModel: Any?,
    afterModel: Any?,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0.5f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                BeforeAfterSlider(
                    beforeModel = beforeModel,
                    afterModel = afterModel,
                    progress = progress,
                    onProgressChange = { progress = it },
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Slider(
                        value = progress,
                        onValueChange = { progress = it },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Inline before/after image slider with a draggable handle. Suitable for
 * embedding inside a screen layout (e.g. ResultScreen) or full-bleed inside
 * [CompareOverlay]. Shows a small "Before / After" pill at the top so users
 * understand the affordance.
 */
@Composable
fun BeforeAfterSlider(
    beforeModel: Any?,
    afterModel: Any?,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (widthPx > 0f) {
                        onProgressChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        AsyncImage(
            model = afterModel,
            contentDescription = "After",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = beforeModel,
            contentDescription = "Before",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = progress * size.width) {
                        this@drawWithContent.drawContent()
                    }
                },
        )

        if (widthPx > 0f) {
            val handleDp = with(density) { (progress * widthPx).toDp() }
            // Vertical divider line.
            Box(
                modifier = Modifier
                    .offset(x = handleDp - 1.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.9f)),
            )
            // Drag handle knob, centered vertically.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = handleDp - 18.dp)
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("◂▸", color = Color.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = "Before / After",
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
