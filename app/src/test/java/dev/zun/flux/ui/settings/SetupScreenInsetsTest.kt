package dev.zun.flux.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SYNTHETIC_IME_HEIGHT_PX = 600

/**
 * Reproduces the double-counted-ime-inset bug (feature 005) without a real keyboard:
 * dispatches a synthetic non-zero `ime` WindowInsets to the root view and measures the
 * content area a [Scaffold] + child `.imePadding()` actually leaves, once with the buggy
 * `contentWindowInsets = WindowInsets.safeDrawing` (which already includes `ime`) and once
 * with the fix (`.exclude(WindowInsets.ime)`). This is the exact insets-composition shape
 * shared by both `SetupScreen.kt` and `SettingsScreen.kt` (see `research.md`), so one
 * isolated harness covers the root cause for both rather than duplicating it per screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2520dp")
class SetupScreenInsetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `excluding ime from Scaffold insets avoids double-counting the keyboard height`() {
        var excludeIme by mutableStateOf(false)
        var measuredHeight = 0

        rule.setContent {
            HarnessScreen(excludeIme = excludeIme) { measuredHeight = it }
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            val withIme = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, SYNTHETIC_IME_HEIGHT_PX))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(rule.activity.window.decorView, withIme)
        }
        rule.waitForIdle()
        val buggyHeight = measuredHeight

        excludeIme = true
        rule.waitForIdle()
        val fixedHeight = measuredHeight

        assert(fixedHeight - buggyHeight == SYNTHETIC_IME_HEIGHT_PX) {
            "expected excluding ime from Scaffold's insets to recover exactly the " +
                "double-counted $SYNTHETIC_IME_HEIGHT_PX px, but fixed=$fixedHeight buggy=$buggyHeight " +
                "(diff=${fixedHeight - buggyHeight})"
        }
    }
}

@Composable
private fun HarnessScreen(excludeIme: Boolean, onMeasured: (Int) -> Unit) {
    val scaffoldInsets = if (excludeIme) WindowInsets.safeDrawing.exclude(WindowInsets.ime) else WindowInsets.safeDrawing
    Scaffold(contentWindowInsets = scaffoldInsets) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .onSizeChanged { onMeasured(it.height) },
        )
    }
}
