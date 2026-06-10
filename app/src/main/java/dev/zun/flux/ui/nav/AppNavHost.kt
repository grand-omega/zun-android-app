package dev.zun.flux.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.zun.flux.FluxApp
import dev.zun.flux.Repositories
import dev.zun.flux.ui.capture.CameraScreen
import dev.zun.flux.ui.gallery.GalleryScaffold
import dev.zun.flux.ui.home.HomeRoute
import dev.zun.flux.ui.progress.BatchProgressScreen
import dev.zun.flux.ui.progress.ProgressScreen
import dev.zun.flux.ui.result.ResultScreen
import dev.zun.flux.ui.settings.SettingsScreen
import dev.zun.flux.ui.settings.SetupScreen

/**
 * Saved-state key the result screen uses to tell its caller (Batch or Home)
 * that a job was just deleted. Recipients should remove it from any locally
 * cached id list and clear the value.
 */
private const val KEY_DELETED_JOB_ID = "deletedJobId"

@Composable
fun AppNavHost(
    repositories: Repositories,
    repositoryVersion: Long,
    sharedUris: List<android.net.Uri> = emptyList(),
    onSharedUrisConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as FluxApp
    val settingsManager = app.settingsManager
    val startDestination = if (settingsManager.isConfigured) Routes.HOME else Routes.SETUP

    val nav = rememberNavController()
    val slideSpec = tween<IntOffset>(durationMillis = 300)
    val fadeSpec = tween<Float>(durationMillis = 300)
    NavHost(
        navController = nav,
        startDestination = startDestination,
        // Forward navigation: new screen slides in from the right.
        enterTransition = { slideInHorizontally(slideSpec) { it } + fadeIn(fadeSpec) },
        exitTransition = { slideOutHorizontally(slideSpec) { -it / 4 } + fadeOut(fadeSpec) },
        // Back navigation: predictive-back drives this in sync with the gesture.
        popEnterTransition = { slideInHorizontally(slideSpec) { -it / 4 } + fadeIn(fadeSpec) },
        popExitTransition = { slideOutHorizontally(slideSpec) { it } + fadeOut(fadeSpec) },
    ) {
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

            HomeRoute(
                healthRepo = repositories.health,
                promptRepo = repositories.prompts,
                jobRepo = repositories.jobs,
                uploadRepo = repositories.uploads,
                images = repositories.images,
                repositoryVersion = repositoryVersion,
                capturedUri = capturedUri,
                sharedUris = sharedUris,
                onSharedUrisConsumed = onSharedUrisConsumed,
                onTakePhoto = { nav.navigate(Routes.CAMERA) },
                onGalleryClick = { nav.navigate(Routes.GALLERY) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
                onJobSubmitted = { jobId ->
                    nav.navigate(Routes.progress(jobId))
                },
                onBatchSubmitted = { jobIds ->
                    nav.navigate(Routes.batch(jobIds))
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
                jobs = repositories.jobs,
                prompts = repositories.prompts,
                images = repositories.images,
                repositoryVersion = repositoryVersion,
                onUseInput = { uri ->
                    nav.getBackStackEntry(Routes.HOME).savedStateHandle["capturedUri"] = uri
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
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
                jobs = repositories.jobs,
                prompts = repositories.prompts,
                images = repositories.images,
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
                jobs = repositories.jobs,
                promptRepo = repositories.prompts,
                uploads = repositories.uploads,
                images = repositories.images,
                onRegenerated = { newJobId ->
                    nav.navigate(Routes.progress(newJobId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onNewImage = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onDeleted = {
                    nav.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_DELETED_JOB_ID, jobId)
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.BATCH_PROGRESS) { entry ->
            val originalJobIds = remember(entry) {
                entry.arguments?.getString("jobIds").orEmpty()
                    .split(",")
                    .filter { it.isNotEmpty() }
            }
            val savedState = entry.savedStateHandle
            val deletedJobId by savedState
                .getStateFlow<String?>(KEY_DELETED_JOB_ID, null)
                .collectAsStateWithLifecycle()
            val pendingDeletedIds by repositories.jobs.deletedJobIds()
                .collectAsStateWithLifecycle(initialValue = emptySet())
            // Track removals so deleted tiles disappear from the grid.
            val removedIds: SnapshotStateList<String> = remember { mutableStateListOf<String>() }
            LaunchedEffect(deletedJobId) {
                val id = deletedJobId
                if (id != null) {
                    if (id !in removedIds) removedIds.add(id)
                    savedState[KEY_DELETED_JOB_ID] = null
                }
            }
            LaunchedEffect(pendingDeletedIds) {
                pendingDeletedIds.forEach { id ->
                    if (id in originalJobIds && id !in removedIds) removedIds.add(id)
                }
            }
            val visibleJobIds = originalJobIds.filter { it !in removedIds }

            // If everything's been deleted, drop back to Home — there's nothing
            // left for this batch screen to show.
            LaunchedEffect(visibleJobIds.isEmpty()) {
                if (visibleJobIds.isEmpty()) {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                }
            }

            if (visibleJobIds.isNotEmpty()) {
                BatchProgressScreen(
                    jobIds = visibleJobIds,
                    jobs = repositories.jobs,
                    images = repositories.images,
                    onViewResult = { id -> nav.navigate(Routes.result(id)) },
                    onBack = { nav.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
        }
    }
}
