package dev.zun.flux.ui.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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

    private fun job(stackCount: Int) = JobSummaryDto(
        id = "job-1",
        created_at = 0L,
        stackCount = stackCount,
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
}
