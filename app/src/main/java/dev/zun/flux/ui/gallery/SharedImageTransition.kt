package dev.zun.flux.ui.gallery

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Scopes for the gallery-grid ↔ photo-viewer shared-element transition,
 * provided by [GalleryScaffold]. Null (the default) when a screen is composed
 * outside the scaffold — e.g. directly in tests — in which case
 * [sharedImageBounds] is a no-op.
 */
internal val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalPaneAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Tags this composable as showing the image for [jobId], so the grid
 * thumbnail and the viewer page animate as one element across the
 * list/detail pane transition.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedImageBounds(jobId: String): Modifier {
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalPaneAnimatedVisibilityScope.current ?: return this
    return with(transitionScope) {
        this@sharedImageBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "job-image-$jobId"),
            animatedVisibilityScope = visibilityScope,
        )
    }
}
