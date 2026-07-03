package dev.zun.flux.ui.gallery

import android.net.Uri
import androidx.compose.animation.fadeOut
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.data.repo.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GalleryScaffold(
    jobs: JobRepository,
    prompts: PromptRepository,
    images: ImageSourceRepository,
    repositoryVersion: Long,
    onUseInput: (Uri) -> Unit,
    onBack: () -> Unit,
    settings: SettingsManager? = null,
) {
    val viewModel: GalleryViewModel =
        viewModel(
            key = "gallery-$repositoryVersion",
            factory =
            viewModelFactory {
                initializer { GalleryViewModel(jobs, prompts, images, settings) }
            },
        )
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                GalleryScreen(
                    images = images,
                    viewModel = viewModel,
                    onJobClick = { jobId ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, jobId) }
                    },
                    onBack = onBack,
                    showUndoSnackbars = navigator.currentDestination?.contentKey == null,
                )
            }
        },
        detailPane = {
            // Fade out only — no slide — when leaving the photo viewer back to
            // the gallery list, so predictive-back doesn't tow a heavy dark
            // pane across the screen.
            AnimatedPane(exitTransition = fadeOut()) {
                val jobId = navigator.currentDestination?.contentKey
                if (jobId != null) {
                    PhotoViewerScreen(
                        initialJobId = jobId,
                        viewModel = viewModel,
                        images = images,
                        onUseInput = onUseInput,
                        onBack = { scope.launch { navigator.navigateBack() } },
                    )
                }
            }
        },
    )
}
