package dev.zun.flux.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coverage for the Home entry point's reactivity: it must stay hidden at
 * zero, reflect the current count, and update as that count changes —
 * exactly what makes it trustworthy as jobs individually complete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-normal-port")
class HomeActiveJobsBannerTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `is not displayed when count is zero`() {
        rule.setContent {
            ActiveJobsBanner(count = 0, onClick = {})
        }

        val children = rule.onRoot().fetchSemanticsNode().children
        assertTrue("Expected no banner content, found $children", children.isEmpty())
    }

    @Test
    fun `shows the count and updates as it changes`() {
        var count by mutableIntStateOf(2)
        rule.setContent {
            ActiveJobsBanner(count = count, onClick = {})
        }

        rule.onNodeWithText("2 generations").assertIsDisplayed()

        count = 1
        rule.onNodeWithText("1 generation").assertIsDisplayed()

        count = 0
        rule.onNodeWithText("1 generation").assertDoesNotExist()
    }
}
