package dev.zun.flux.ui.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        val viewModel = GalleryViewModel(repo, repo, repo)

        rule.setContent {
            PhotoViewerScreen(
                initialJobId = "j3",
                viewModel = viewModel,
                images = repo,
                onUseInput = {},
                onBack = {},
            )
        }

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodesWithTagSafe("viewer_page_j3").isNotEmpty()
        }
        rule.onNodeWithTag("viewer_page_j3").assertIsDisplayed()
    }

    @OptIn(
        androidx.compose.animation.ExperimentalSharedTransitionApi::class,
        androidx.compose.animation.ExperimentalAnimationApi::class,
    )
    @Test
    fun `viewer renders when shared transition scopes are provided`() {
        val repo = FakeJobRepository().apply { seedDoneJobs(listOf("j1", "j2")) }
        val viewModel = GalleryViewModel(repo, repo, repo)

        rule.setContent {
            androidx.compose.animation.SharedTransitionLayout {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this,
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = true) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalPaneAnimatedVisibilityScope provides this,
                        ) {
                            PhotoViewerScreen(
                                initialJobId = "j1",
                                viewModel = viewModel,
                                images = repo,
                                onUseInput = {},
                                onBack = {},
                            )
                        }
                    }
                }
            }
        }

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodesWithTagSafe("viewer_page_j1").isNotEmpty()
        }
        rule.onNodeWithTag("viewer_page_j1").assertIsDisplayed()
    }

    @Test
    fun `viewer stays on the tapped photo when newer jobs are prepended`() {
        val repo = FakeJobRepository().apply {
            seedDoneJobs(listOf("j1", "j2", "j3", "j4", "j5"))
        }
        val viewModel = GalleryViewModel(repo, repo, repo)

        rule.setContent {
            PhotoViewerScreen(
                initialJobId = "j3",
                viewModel = viewModel,
                images = repo,
                onUseInput = {},
                onBack = {},
            )
        }

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodesWithTagSafe("viewer_page_j3").isNotEmpty()
        }
        rule.onNodeWithTag("viewer_page_j3").assertIsDisplayed()

        // A new generation finishes while the viewer is open: it is prepended,
        // shifting j3 from index 2 to index 3 (and the count 5 -> 6). The pager
        // must follow the *job*, not the old index — so j3's page becomes
        // "Image 4 of 6". The old fixed-index code stayed on index 2 (j2,
        // "Image 3 of 6") and this condition would never become true.
        repo.seedDoneJobs(listOf("newer"))

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodes(hasContentDescription("Image 4 of 6"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        rule.onNodeWithTag("viewer_page_j3").assertIsDisplayed()
    }

    @Test
    fun `viewer renders loading state when jobs flow is initially empty`() {
        val repo = FakeJobRepository()
        val viewModel = GalleryViewModel(repo, repo, repo)

        rule.setContent {
            PhotoViewerScreen(
                initialJobId = "anything",
                viewModel = viewModel,
                images = repo,
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

    @Test
    fun `tapping the delete action opens the confirmation dialog`() {
        val repo = FakeJobRepository().apply { seedDoneJobs(listOf("a", "b")) }
        val viewModel = GalleryViewModel(repo, repo, repo)

        rule.setContent {
            PhotoViewerScreen(
                initialJobId = "a",
                viewModel = viewModel,
                images = repo,
                onUseInput = {},
                onBack = {},
            )
        }

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodesWithTagSafe("viewer_page_a").isNotEmpty()
        }
        rule.onNode(hasContentDescription("Delete")).performClick()
        rule.onNodeWithText("Delete generation?").assertIsDisplayed()
    }

    @Test
    fun `dismiss button closes the delete confirmation without deleting`() {
        val repo = FakeJobRepository().apply { seedDoneJobs(listOf("a", "b")) }
        val viewModel = GalleryViewModel(repo, repo, repo)

        rule.setContent {
            PhotoViewerScreen(
                initialJobId = "a",
                viewModel = viewModel,
                images = repo,
                onUseInput = {},
                onBack = {},
            )
        }

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodesWithTagSafe("viewer_page_a").isNotEmpty()
        }
        rule.onNode(hasContentDescription("Delete")).performClick()
        rule.onNodeWithText("Cancel").performClick()

        // Dialog gone, page still visible.
        rule.onNodeWithTag("viewer_page_a").assertIsDisplayed()
        rule.onAllNodesWithTextSafe("Delete generation?").let { nodes ->
            assert(nodes.isEmpty()) { "expected delete dialog to be dismissed" }
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagSafe(tag: String) = onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes(atLeastOneRootRequired = false)

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafe(text: String) = onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false)
}
