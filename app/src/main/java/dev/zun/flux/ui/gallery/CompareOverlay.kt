package dev.zun.flux.ui.gallery

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.zun.flux.R

/** Which before/after comparison interaction is currently shown. */
enum class CompareMode { Slider, Scratch }

/** Default brush radius for [ScratchRevealCompare]'s reveal stamps. */
val DEFAULT_SCRATCH_BRUSH_RADIUS = 40.dp

/**
 * Self-contained before/after comparison widget: switches between [BeforeAfterSlider] and
 * [ScratchRevealCompare] via a small toggle button, hoisting each mode's own progress internally
 * so switching back and forth never loses either one (feature 010 research.md Decision 3).
 * Used full-bleed inside [CompareOverlay] (the gallery photo viewer) and embedded inline in
 * `ResultScreen` right after a generation finishes — same widget, different host chrome around it.
 */
@Composable
fun CompareModeSwitcher(
    beforeModel: Any?,
    afterModel: Any?,
    initialMode: CompareMode,
    onSaveComposite: suspend (Bitmap) -> Result<Unit>,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0.5f) }
    var mode by remember { mutableStateOf(initialMode) }
    var brushRadius by remember { mutableStateOf(DEFAULT_SCRATCH_BRUSH_RADIUS) }
    // Owned here (not inside ScratchRevealCompare) so it survives switching to Slider and back —
    // see feature 010 research.md Decision 3 / tasks.md T004's correction.
    var maskBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val switchModeDescription = stringResource(
        if (mode == CompareMode.Slider) R.string.compare_switch_mode_to_scratch else R.string.compare_switch_mode_to_slider,
    )

    Box(modifier = modifier.fillMaxSize()) {
        when (mode) {
            CompareMode.Slider -> BeforeAfterSlider(
                beforeModel = beforeModel,
                afterModel = afterModel,
                progress = progress,
                onProgressChange = { progress = it },
            )

            CompareMode.Scratch -> ScratchRevealCompare(
                beforeModel = beforeModel,
                afterModel = afterModel,
                brushRadius = brushRadius,
                maskBitmap = maskBitmap,
                onSizeKnown = { size ->
                    if (maskBitmap == null || maskBitmap?.width != size.width || maskBitmap?.height != size.height) {
                        maskBitmap = createOpaqueMask(size)
                    }
                },
                onBrushRadiusChange = { brushRadius = it },
                onSaveComposite = onSaveComposite,
            )
        }

        // Bottom-start: mode toggle. Placed here (not top-start) because this app's own
        // convention already puts back/close navigation at top-start/top-end — a second control
        // there risks being tapped by habit instead of understood. Icon-only, same compact
        // footprint as the close/reset buttons elsewhere in this overlay — a visible text label
        // took up too much space next to a single image; the contentDescription still carries the
        // full "switch to X mode" meaning for screen readers.
        IconButton(
            onClick = { mode = if (mode == CompareMode.Slider) CompareMode.Scratch else CompareMode.Slider },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = switchModeDescription,
                tint = Color.White,
            )
        }

        // Bottom-end: reset (scratch mode only — the slider has no destructive state to undo).
        // Recreates the mask directly since this composable already owns it (see above) — no
        // need to plumb a "please reset" signal down into ScratchRevealCompare.
        if (mode == CompareMode.Scratch) {
            IconButton(
                onClick = {
                    maskBitmap?.let { current ->
                        maskBitmap = createOpaqueMask(IntSize(current.width, current.height))
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.compare_scratch_reset),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
fun CompareOverlay(
    beforeModel: Any?,
    afterModel: Any?,
    initialMode: CompareMode = CompareMode.Slider,
    onSaveComposite: suspend (Bitmap) -> Result<Unit>,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CompareModeSwitcher(
            beforeModel = beforeModel,
            afterModel = afterModel,
            initialMode = initialMode,
            onSaveComposite = onSaveComposite,
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White)
        }
    }
}

private fun createOpaqueMask(size: IntSize): ImageBitmap {
    val bitmap = ImageBitmap(size.width, size.height)
    Canvas(bitmap).drawRect(
        left = 0f,
        top = 0f,
        right = size.width.toFloat(),
        bottom = size.height.toFloat(),
        paint = Paint().apply { color = Color.Black },
    )
    return bitmap
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
            contentDescription = stringResource(R.string.result_after),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        AsyncImage(
            model = beforeModel,
            contentDescription = stringResource(R.string.result_before),
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
                text = stringResource(R.string.compare_before_after),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
