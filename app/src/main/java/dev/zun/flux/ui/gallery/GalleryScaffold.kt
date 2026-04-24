package dev.zun.flux.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GalleryScaffold(
    repository: JobRepository,
    onBack: () -> Unit,
) {
    val viewModel: GalleryViewModel =
        viewModel(
            factory =
            viewModelFactory {
                initializer { GalleryViewModel(repository) }
            },
        )
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()

    BackHandler(navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                GalleryScreen(
                    repository = repository,
                    viewModel = viewModel,
                    onJobClick = { jobId ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, jobId) }
                    },
                    onBack = onBack,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val jobId = navigator.currentDestination?.contentKey
                if (jobId != null) {
                    PhotoViewerScreen(
                        initialJobId = jobId,
                        viewModel = viewModel,
                        repository = repository,
                        onBack = { scope.launch { navigator.navigateBack() } },
                    )
                }
            }
        },
    )
}
