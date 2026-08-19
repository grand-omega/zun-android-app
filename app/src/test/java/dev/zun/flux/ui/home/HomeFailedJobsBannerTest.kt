package dev.zun.flux.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Coverage for the Home entry point into failed generations — see [FailedJobsBanner]. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-normal-port")
class HomeFailedJobsBannerTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `is not displayed when nothing has failed`() {
        rule.setContent {
            FailedJobsBanner(count = 0, onClick = {})
        }

        val children = rule.onRoot().fetchSemanticsNode().children
        assertTrue("Expected no banner content, found $children", children.isEmpty())
    }

    @Test
    fun `shows the count and updates as failures are cleared`() {
        var count by mutableIntStateOf(2)
        rule.setContent {
            FailedJobsBanner(count = count, onClick = {})
        }

        rule.onNodeWithText("2 generations failed").assertIsDisplayed()

        // Dismissing one from batch progress deletes its local record, so the count drops.
        count = 1
        rule.onNodeWithText("1 generation failed").assertIsDisplayed()
    }

    @Test
    fun `tapping opens the review destination`() {
        var clicks = 0
        rule.setContent {
            FailedJobsBanner(count = 1, onClick = { clicks++ })
        }

        rule.onNodeWithText("1 generation failed").performClick()

        assertEquals(1, clicks)
    }
}
