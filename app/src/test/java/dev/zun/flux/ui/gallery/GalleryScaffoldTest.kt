package dev.zun.flux.ui.gallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.zun.flux.data.repo.FakeJobRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke coverage for the scaffold wiring: the list/detail panes must compose
 * without blowing up and render the gallery list pane.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-normal-port")
class GalleryScaffoldTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `scaffold renders the gallery list pane inside the shared transition layout`() {
        val repo = FakeJobRepository().apply { seedDoneJobs(listOf("a", "b")) }

        rule.setContent {
            GalleryScaffold(
                jobs = repo,
                prompts = repo,
                images = repo,
                repositoryVersion = 0L,
                onUseInput = {},
                onBack = {},
            )
        }

        rule.waitUntil(timeoutMillis = 3_000) {
            rule.onAllNodesWithTextSafe("Gallery").isNotEmpty()
        }
        rule.onNodeWithText("Gallery").assertIsDisplayed()
    }

    private fun ComposeContentTestRule.onAllNodesWithTextSafe(text: String) = onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false)
}
