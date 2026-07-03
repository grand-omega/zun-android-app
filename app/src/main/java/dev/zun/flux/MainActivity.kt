package dev.zun.flux

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

/** Launcher-shortcut action (see res/xml/shortcuts.xml). */
private const val ACTION_GALLERY = "dev.zun.flux.action.GALLERY"

class MainActivity : FragmentActivity() {
    private var unlockMessage by mutableStateOf<String?>(null)
    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())
    private var pendingGalleryNav by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        sharedUris = sharedImageUris(intent)
        pendingGalleryNav = intent?.action == ACTION_GALLERY
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
            val repoState by app.repositoryState.collectAsStateWithLifecycle()
            ZunFluxTheme {
                val current = repoState
                if (auth.isAuthed && current != null) {
                    AppNavHost(
                        repositories = current.repositories,
                        repositoryVersion = current.version,
                        sharedUris = sharedUris,
                        onSharedUrisConsumed = { sharedUris = emptyList() },
                        navigateToGallery = pendingGalleryNav,
                        onGalleryNavConsumed = { pendingGalleryNav = false },
                    )
                } else {
                    LockScreen(
                        message = unlockMessage,
                        onUnlockClick = { tryUnlock(auth) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uris = sharedImageUris(intent)
        if (uris.isNotEmpty()) sharedUris = uris
        if (intent.action == ACTION_GALLERY) pendingGalleryNav = true
    }

    /** Image URIs delivered via the system share sheet (ACTION_SEND[_MULTIPLE]). */
    private fun sharedImageUris(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))

        Intent.ACTION_SEND_MULTIPLE ->
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()

        else -> emptyList()
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
