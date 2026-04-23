package dev.zun.flux.ui.nav

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.zun.flux.FluxApp
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.ui.capture.CameraScreen
import dev.zun.flux.ui.gallery.GalleryScaffold
import dev.zun.flux.ui.home.HomeScreen
import dev.zun.flux.ui.progress.ProgressScreen
import dev.zun.flux.ui.result.ResultScreen
import dev.zun.flux.ui.settings.SettingsScreen
import dev.zun.flux.ui.settings.SetupScreen

@Composable
fun AppNavHost(
    repository: JobRepository,
    windowSizeClass: WindowSizeClass,
) {
    val context = LocalContext.current
    val app = context.applicationContext as FluxApp
    val settingsManager = app.settingsManager
    val startDestination = if (settingsManager.isConfigured) Routes.HOME else Routes.SETUP

    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.HOME) { entry ->
            // Check for photo capture result
            var capturedUri by remember { mutableStateOf<android.net.Uri?>(null) }
            val savedState = entry.savedStateHandle
            LaunchedEffect(savedState) {
                val uri = savedState.get<android.net.Uri>("capturedUri")
                if (uri != null) {
                    capturedUri = uri
                    savedState.remove<android.net.Uri>("capturedUri")
                }
            }

            HomeScreen(
                repository = repository,
                windowSizeClass = windowSizeClass,
                capturedUri = capturedUri,
                onTakePhoto = { nav.navigate(Routes.CAMERA) },
                onGalleryClick = { nav.navigate(Routes.GALLERY) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
                onJobSubmitted = { jobId ->
                    nav.navigate(Routes.progress(jobId))
                },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                app = app,
                onSuccess = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                onCaptured = { uri ->
                    nav.previousBackStackEntry?.savedStateHandle?.set("capturedUri", uri)
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.GALLERY) {
            GalleryScaffold(
                repository = repository,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                app = app,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.PROGRESS) { entry ->
            val jobId = entry.arguments?.getString("jobId").orEmpty()
            ProgressScreen(
                jobId = jobId,
                repository = repository,
                onDone = {
                    nav.navigate(Routes.result(jobId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.RESULT) { entry ->
            val jobId = entry.arguments?.getString("jobId").orEmpty()
            ResultScreen(
                jobId = jobId,
                repository = repository,
                windowSizeClass = windowSizeClass,
                onTryAnotherPrompt = { source ->
                    val uri = when (source) {
                        is android.net.Uri -> source
                        is String -> source.toUri()
                        else -> null
                    }
                    nav.previousBackStackEntry?.savedStateHandle?.set("capturedUri", uri)
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
                onBack = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
