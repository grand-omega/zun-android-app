package dev.zun.flux.ui.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.zun.flux.R
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.OfflineImageAvailability
import dev.zun.flux.ui.common.MissingImageState
import dev.zun.flux.util.resolvePromptLabel

/**
 * Duration of the "developing" reveal (feature 011) — comfortably under the 1-second bound
 * FR-005/SC-003 require; a `tween` is used deliberately instead of the implicit default spring,
 * whose settling time isn't guaranteed to land under that bound.
 */
private const val REVEAL_DURATION_MS = 450
private val REVEAL_MAX_BLUR = 16.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun JobThumbnail(
    job: JobSummaryDto,
    prompts: List<PromptDto>,
    model: Any?,
    availability: OfflineImageAvailability?,
    showMetadata: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRevealEligible: Boolean = false,
    onRevealPlayed: (String) -> Unit = {},
) {
    // Captured once per job.id: eligible thumbnails start blurred/soft and animate to settled;
    // ineligible ones start (and stay) fully settled, no animation, no delay.
    var revealTarget by remember(job.id) { mutableFloatStateOf(if (isRevealEligible) 0f else 1f) }
    val revealProgress by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = tween(durationMillis = REVEAL_DURATION_MS),
        label = "thumbnailReveal",
    )
    LaunchedEffect(job.id) {
        if (isRevealEligible) {
            // Consume immediately — before the animation plays out, not after — so a disposed
            // (navigated away, scrolled off) mid-animation cell can never replay on a later
            // composition (feature 011 research.md Decision 2; ties FR-002 to FR-006).
            onRevealPlayed(job.id)
            revealTarget = 1f
        }
    }

    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
            ),
        colors =
        CardDefaults.cardColors(
            containerColor =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        border =
        if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        val promptLabel = resolvePromptLabel(prompts, job.effectivePromptId, job.prompt_text)
        val tileDescription = stringResource(R.string.gallery_thumbnail_description, promptLabel)
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = tileDescription,
                contentScale = ContentScale.Crop,
                modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (isSelected) Modifier.padding(8.dp).clip(RoundedCornerShape(4.dp)) else Modifier,
                    )
                    .graphicsLayer {
                        val scale = 0.92f + 0.08f * revealProgress
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.3f + 0.7f * revealProgress
                    }
                    .blur(REVEAL_MAX_BLUR * (1f - revealProgress)),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                },
                error = {
                    MissingThumbnail()
                },
                success = {
                    SubcomposeAsyncImageContent()
                },
            )

            if (!isSelectionMode && availability?.resultCached == false) {
                NeedsNetworkIcon(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                )
            }

            if (!isSelectionMode && job.stackCount > 1) {
                Text(
                    text = job.stackCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (!isSelectionMode && job.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = stringResource(R.string.gallery_favorited),
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(3.dp)
                        .size(16.dp),
                )
            } else if (!isSelectionMode && job.stackCount > 1 && job.stackHasFavorite) {
                // This stack's cover isn't itself favorited, but another variant in it is — a
                // half-filled heart signals "something in here is favorited" without implying
                // the whole stack (or this specific cover) was favorited.
                HalfFavoriteIcon(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(3.dp)
                        .size(16.dp),
                )
            }

            if (!isSelectionMode && showMetadata) {
                Text(
                    text = resolvePromptLabel(prompts, job.effectivePromptId, job.prompt_text),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 2,
                    modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.gallery_selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * A heart that's solid on its left half and outlined on its right half — "some, not all, of this
 * stack is favorited," visually distinct from both the full solid heart (this cover itself is
 * favorited) and no heart at all (nothing in this stack is favorited).
 */
@Composable
private fun HalfFavoriteIcon(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.FavoriteBorder,
            contentDescription = stringResource(R.string.gallery_stack_partially_favorited),
            tint = Color.White,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width / 2f) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}

@Composable
private fun NeedsNetworkIcon(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = stringResource(R.string.gallery_not_cached_needs_network),
            tint = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun MissingThumbnail() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        MissingImageState(label = stringResource(R.string.gallery_unavailable), modifier = Modifier.fillMaxSize())
    }
}
