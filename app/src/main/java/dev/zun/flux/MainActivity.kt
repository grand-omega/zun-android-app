package dev.zun.flux

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.zun.flux.ui.auth.BiometricResult
import dev.zun.flux.ui.auth.LockScreen
import dev.zun.flux.ui.auth.promptBiometric
import dev.zun.flux.ui.nav.AppNavHost
import dev.zun.flux.ui.theme.ZunFluxTheme

private const val TAG = "MainActivity"

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val app = application as FluxApp
        val repo = app.repository
        val auth = app.authStateHolder

        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    auth.checkLock()
                    if (!auth.isAuthed) {
                        tryUnlock(auth)
                    }
                }
            },
        )

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            ZunFluxTheme {
                if (auth.isAuthed) {
                    AppNavHost(
                        repository = repo,
                        windowSizeClass = windowSizeClass,
                    )
                } else {
                    LockScreen(onUnlockClick = { tryUnlock(auth) })
                }
            }
        }
    }

    private fun tryUnlock(auth: dev.zun.flux.ui.auth.AuthStateHolder) {
        promptBiometric { result ->
            when (result) {
                BiometricResult.Success -> auth.markAuthed()
                BiometricResult.Unavailable -> {
                    Log.i(TAG, "Biometric hardware unavailable or unsupported. Skipping gate.")
                    auth.markAuthed()
                }
                is BiometricResult.Error -> {
                    Log.e(TAG, "Biometric error: ${result.message}")
                    // In a real app, maybe show a toast or exit if it's a hard error
                    // For now, let the user retry via the button on LockScreen
                }
            }
        }
    }
}
