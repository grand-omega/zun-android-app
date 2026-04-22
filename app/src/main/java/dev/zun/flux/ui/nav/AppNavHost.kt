package dev.zun.flux.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.ui.home.HomeScreen
import dev.zun.flux.ui.progress.ProgressScreen
import dev.zun.flux.ui.result.ResultScreen

@Composable
fun AppNavHost(repository: JobRepository) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onJobSubmitted = { jobId ->
                    nav.navigate(Routes.progress(jobId))
                },
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
                onBack = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
