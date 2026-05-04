package dev.zun.flux

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zun.flux.ui.auth.BiometricResult
import dev.zun.flux.ui.auth.LockScreen
import dev.zun.flux.ui.auth.promptBiometric
import dev.zun.flux.ui.nav.AppNavHost
import dev.zun.flux.ui.theme.ZunFluxTheme

private const val TAG = "MainActivity"

class MainActivity : FragmentActivity() {
    private var unlockMessage by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val app = application as FluxApp
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
            val repoState by app.repositoryState.collectAsStateWithLifecycle()
            ZunFluxTheme {
                val current = repoState
                if (auth.isAuthed && current != null) {
                    key(current.version) {
                        AppNavHost(
                            repository = current.repository,
                            repositoryVersion = current.version,
                            windowSizeClass = windowSizeClass,
                        )
                    }
                } else {
                    LockScreen(
                        message = unlockMessage,
                        onUnlockClick = { tryUnlock(auth) },
                    )
                }
            }
        }
    }

    private fun tryUnlock(auth: dev.zun.flux.ui.auth.AuthStateHolder) {
        promptBiometric { result ->
            when (result) {
                BiometricResult.Success -> {
                    unlockMessage = null
                    auth.markAuthed()
                }

                BiometricResult.Unavailable -> {
                    Log.w(TAG, "Biometric or device credential auth unavailable. Keeping app locked.")
                    unlockMessage = "Set up a screen lock or biometric credential to use FluxEdit."
                }

                is BiometricResult.Error -> {
                    Log.e(TAG, "Biometric error: ${result.message}")
                    unlockMessage = result.message
                }
            }
        }
    }
}
