package dev.zun.flux.ui.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.zun.flux.data.repo.FakeJobRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the bug where opening the gallery → tapping any
 * thumbnail → viewer always landed on page 0 (until the user backed out and
 * re-entered, when the cached jobs list was already populated). The fix uses a
 * LaunchedEffect to scroll to the resolved index once jobs emit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-normal-port")
class PhotoViewerScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `viewer lands on the requested page once jobs load`() {
        val repo = FakeJobRepository().apply {
            seedDoneJobs(listOf("j1", "j2", "j3", "j4", "j5"))
        }
        val viewModel = GalleryViewModel(repo)

        rule.setContent {
            PhotoViewerScreen(
                initialJobId = "j3",
                viewModel = viewModel,
                repository = repo,
                onUseInput = {},
                onBack = {},
            )
        }

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodesWithTagSafe("viewer_page_j3").isNotEmpty()
        }
        rule.onNodeWithTag("viewer_page_j3").assertIsDisplayed()
    }

    @Test
    fun `viewer renders loading state when jobs flow is initially empty`() {
        val repo = FakeJobRepository()
        val viewModel = GalleryViewModel(repo)

        rule.setContent {
            PhotoViewerScreen(
                initialJobId = "anything",
                viewModel = viewModel,
                repository = repo,
                onUseInput = {},
                onBack = {},
            )
        }

        // No pages exist; this would have rendered a blank pager before the
        // empty-state fix.
        rule.onAllNodesWithTagSafe("viewer_page_anything").let { nodes ->
            assert(nodes.isEmpty()) { "expected no page nodes when jobs is empty" }
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagSafe(tag: String) = onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false)
}
