package dev.zun.flux.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-normal-port")
class LockScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `lock screen renders title and unlock button`() {
        rule.setContent { LockScreen(message = null, onUnlockClick = {}) }

        rule.onNodeWithText("FluxEdit is locked").assertIsDisplayed()
        rule.onNodeWithText("Unlock").assertIsDisplayed()
    }

    @Test
    fun `unlock button click invokes callback`() {
        var clicked = false
        rule.setContent { LockScreen(message = null, onUnlockClick = { clicked = true }) }

        rule.onNodeWithText("Unlock").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `error message is displayed when provided`() {
        rule.setContent { LockScreen(message = "Biometric unavailable", onUnlockClick = {}) }

        rule.onNodeWithText("Biometric unavailable").assertIsDisplayed()
    }
}
