package dev.zun.flux.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import dev.zun.flux.data.api.PromptDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduces the feature-006 bug without a screenshot (pixel capture is
 * blocked by this app's own FLAG_SECURE — see research.md): renders
 * [PromptLibraryContent] the same way the wide/unfolded Home pane does
 * (`fillHeight = true`) inside a height-constrained harness, with the custom
 * "Write your own…" field expanded, and asserts its Save button is
 * reachable by scrolling — the exact property FR-002 requires. Before the
 * fix, the header (including the expanded custom field) sits outside the
 * only scrollable region (the prompt-row list), so no amount of scrolling
 * reveals it once it's pushed past the constrained height.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h500dp")
class PromptLibraryContentBoundsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `custom prompt field is reachable by scrolling under a constrained height`() {
        var customText by mutableStateOf("hello world")

        rule.setContent {
            Box(Modifier.height(220.dp)) {
                PromptLibraryContent(
                    prompts = builtInPrompts,
                    selectedPromptId = CUSTOM_PROMPT_ID,
                    customPromptText = customText,
                    onCustomPromptChange = { customText = it },
                    tryHarder = false,
                    onTryHarderChange = {},
                    onSavePromptClick = {},
                    onManagePrompts = {},
                    onSelectPrompt = {},
                    fillHeight = true,
                    showTryHarder = true,
                )
            }
        }

        // Ask the prompt list to scroll until the typed text is visible —
        // the OutlinedTextField also exposes a scroll action (for its own
        // text), so target the LazyColumn specifically via its CollectionInfo.
        val hasCollectionInfo = SemanticsMatcher("has CollectionInfo") {
            it.config.contains(SemanticsProperties.CollectionInfo)
        }
        rule.onAllNodes(hasScrollAction() and hasCollectionInfo).onFirst()
            .performScrollToNode(hasText("hello world"))

        rule.onNodeWithText("hello world").assertIsDisplayed()
    }

    private val builtInPrompts = listOf(
        PromptDto(id = 1, label = "Prompt One"),
        PromptDto(id = 2, label = "Prompt Two"),
        PromptDto(id = 3, label = "Prompt Three"),
        PromptDto(id = 4, label = "Prompt Four"),
        PromptDto(id = 5, label = "Prompt Five"),
    )
}
