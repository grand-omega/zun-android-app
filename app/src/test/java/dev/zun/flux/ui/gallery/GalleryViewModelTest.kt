package dev.zun.flux.ui.gallery

import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.RecordingRepository
import dev.zun.flux.ui.home.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Covers [GalleryViewModel.favoritesOnly] — an independent filter dimension that must
 * combine with (not replace) the existing prompt-based [TagFilter], per feature 008's spec
 * FR-003. Uses a small dedicated fake (not [dev.zun.flux.data.repo.FakeJobRepository], which
 * models job-queue timing via real wall-clock `Uri`-backed entries and would need Robolectric
 * for no benefit here) so [jobs][GalleryViewModel.jobs]'s content is directly controllable.
 *
 * [GalleryViewModel.jobs] is a multi-layer `combine(...).stateIn(..., SharingStarted.Lazily,
 * ...)` chain; passively reading `.value` after starting a `collect {}` in a background job is
 * unreliable under `runTest`'s virtual scheduler here (its first real emission never lands
 * before the assertion runs). Actively awaiting via `first { predicate }` does not have this
 * problem — it is the pattern used throughout this file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class TestJobRepository : JobRepository by RecordingRepository() {
        private val jobsFlow = MutableStateFlow<List<JobSummaryDto>>(emptyList())

        fun seed(jobs: List<JobSummaryDto>) {
            jobsFlow.value = jobs
        }

        override fun getJobsFlow(): Flow<List<JobSummaryDto>> = jobsFlow

        override suspend fun setFavorite(jobId: String, isFavorite: Boolean) {
            jobsFlow.value = jobsFlow.value.map { if (it.id == jobId) it.copy(isFavorite = isFavorite) else it }
        }
    }

    private fun job(id: String, promptId: Long, isFavorite: Boolean = false) = JobSummaryDto(
        id = id,
        source_prompt_id = promptId,
        created_at = 0L,
        isFavorite = isFavorite,
    )

    private suspend fun Flow<List<JobSummaryDto>>.firstMatching(ids: Set<String>) = withTimeout(5_000) {
        first { it.map { job -> job.id }.toSet() == ids }
    }

    @Test
    fun `favoritesOnly alone narrows to favorited jobs regardless of prompt`() = runTest {
        val repo = TestJobRepository()
        repo.seed(listOf(job("a", promptId = 1L, isFavorite = true), job("b", promptId = 1L), job("c", promptId = 2L, isFavorite = true)))
        val promptRepo = RecordingRepository()
        val viewModel = GalleryViewModel(repo, promptRepo, promptRepo)

        viewModel.setFavoritesOnly(true)

        viewModel.jobs.firstMatching(setOf("a", "c"))
    }

    @Test
    fun `favoritesOnly combines with an active prompt filter as an intersection`() = runTest {
        val repo = TestJobRepository()
        repo.seed(listOf(job("a", promptId = 1L, isFavorite = true), job("b", promptId = 1L), job("c", promptId = 2L, isFavorite = true)))
        val promptRepo = RecordingRepository()
        val viewModel = GalleryViewModel(repo, promptRepo, promptRepo)

        viewModel.setFavoritesOnly(true)
        viewModel.setTagFilter(TagFilter.ByPromptId(1L))

        // Must be the intersection (favorited AND prompt 1) — just 'a', not 'c' (wrong
        // prompt) or 'b' (not favorited).
        viewModel.jobs.firstMatching(setOf("a"))
    }

    @Test
    fun `favoritesOnly off shows everything regardless of favorite status`() = runTest {
        val repo = TestJobRepository()
        repo.seed(listOf(job("a", promptId = 1L, isFavorite = true), job("b", promptId = 1L)))
        val promptRepo = RecordingRepository()
        val viewModel = GalleryViewModel(repo, promptRepo, promptRepo)

        viewModel.jobs.firstMatching(setOf("a", "b"))
    }

    @Test
    fun `setFavorite toggles isFavorite live`() = runTest {
        val repo = TestJobRepository()
        repo.seed(listOf(job("a", promptId = 1L)))
        val promptRepo = RecordingRepository()
        val viewModel = GalleryViewModel(repo, promptRepo, promptRepo)
        viewModel.jobs.firstMatching(setOf("a"))

        viewModel.setFavorite("a", true)

        val updated = withTimeout(5_000) { viewModel.jobs.first { it.any { j -> j.id == "a" && j.isFavorite } } }
        assertEquals(true, updated.first { it.id == "a" }.isFavorite)
    }
}
