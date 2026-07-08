package dev.zun.flux.ui.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.OfflineImageAvailability
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [JobThumbnail] directly rather than through the full paged [GalleryScreen] grid —
 * Robolectric's test scheduler hangs at the Paging 3 loading frame (see the note in
 * `GalleryScreenTest`'s first test), so isolating the thumbnail composable avoids that
 * entirely for this feature's stack-count badge.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryThumbnailTest {

    @get:Rule
    val rule = createComposeRule()

    private fun job(
        stackCount: Int,
        isFavorite: Boolean = false,
        stackHasFavorite: Boolean = false,
        id: String = "job-1",
    ) = JobSummaryDto(
        id = id,
        created_at = 0L,
        stackCount = stackCount,
        isFavorite = isFavorite,
        stackHasFavorite = stackHasFavorite,
    )

    private val availability = OfflineImageAvailability(
        thumbCached = true,
        previewCached = true,
        resultCached = true,
    )

    @Test
    fun `a stack of 3 shows a count badge`() {
        rule.setContent {
            JobThumbnail(
                job = job(stackCount = 3),
                prompts = emptyList(),
                model = null,
                availability = availability,
                showMetadata = false,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
            )
        }

        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun `an unstacked job shows no count badge`() {
        rule.setContent {
            JobThumbnail(
                job = job(stackCount = 1),
                prompts = emptyList(),
                model = null,
                availability = availability,
                showMetadata = false,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
            )
        }

        val badges = rule.onAllNodes(hasText("1")).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("expected no count badge for an unstacked job", badges.isEmpty())
    }

    @Test
    fun `a favorited cover shows the full heart`() {
        rule.setContent {
            JobThumbnail(
                job = job(stackCount = 3, isFavorite = true, stackHasFavorite = true),
                prompts = emptyList(),
                model = null,
                availability = availability,
                showMetadata = false,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
            )
        }

        rule.onNodeWithContentDescription("Favorited").assertIsDisplayed()
    }

    @Test
    fun `a stack whose cover isn't favorited but has a favorited variant shows the half heart`() {
        rule.setContent {
            JobThumbnail(
                job = job(stackCount = 3, isFavorite = false, stackHasFavorite = true),
                prompts = emptyList(),
                model = null,
                availability = availability,
                showMetadata = false,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
            )
        }

        rule.onNodeWithContentDescription("One or more variants in this stack are favorited").assertIsDisplayed()
        val fullHeart = rule.onAllNodes(hasContentDescription("Favorited")).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("expected no full-heart badge, only the half-heart", fullHeart.isEmpty())
    }

    @Test
    fun `a stack with no favorited variants at all shows no heart`() {
        rule.setContent {
            JobThumbnail(
                job = job(stackCount = 3, isFavorite = false, stackHasFavorite = false),
                prompts = emptyList(),
                model = null,
                availability = availability,
                showMetadata = false,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
            )
        }

        val hearts = rule.onAllNodes(
            hasContentDescription("Favorited") or hasContentDescription("One or more variants in this stack are favorited"),
        ).fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("expected no heart at all", hearts.isEmpty())
    }

    @Test
    fun `a saved local composite shows the distinguisher badge`() {
        rule.setContent {
            JobThumbnail(
                job = job(stackCount = 1, id = "local-composite-abc123"),
                prompts = emptyList(),
                model = null,
                availability = availability,
                showMetadata = false,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
            )
        }

        rule.onNodeWithContentDescription("Saved reveal, not an AI-generated result").assertIsDisplayed()
    }

    @Test
    fun `an ordinary AI-generated job shows no distinguisher badge`() {
        rule.setContent {
            JobThumbnail(
                job = job(stackCount = 1, id = "job-1"),
                prompts = emptyList(),
                model = null,
                availability = availability,
                showMetadata = false,
                isSelected = false,
                isSelectionMode = false,
                onClick = {},
            )
        }

        val badges = rule.onAllNodes(hasContentDescription("Saved reveal, not an AI-generated result"))
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("expected no local-composite badge for an ordinary job", badges.isEmpty())
    }
}
