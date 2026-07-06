package dev.zun.flux.ui.home

import android.net.Uri
import dev.zun.flux.data.repo.RecordingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers content-hash deduplication in [HomeViewModel.addInputUris]: a fresh
 * gallery re-pick and a "recent photo" re-download each mint a different
 * [Uri] for what can be byte-identical content, so exact-Uri equality alone
 * lets the same photo end up selected (and processed) twice in one batch.
 * Robolectric is required here for a real, usable [Uri] instance as a map key.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelAddInputUrisTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: HomeViewModel
    private val uriA1 = Uri.parse("content://test/photo-a-pick-1")
    private val uriA2 = Uri.parse("file:///cache/photo-a-recent-download")
    private val uriB = Uri.parse("content://test/photo-b")

    @Before
    fun setUp() {
        val repository = RecordingRepository()
        viewModel = HomeViewModel(
            healthRepo = repository,
            promptRepo = repository,
            jobRepo = repository,
            uploadRepo = repository,
        )
    }

    @Test
    fun addInputUris_rejectsADifferentUriWithTheSameContentHash() = runTest {
        viewModel.addInputUris(listOf(uriA1), hashesOf = mapOf(uriA1 to "hash-a"), maxImages = 10)

        val result = viewModel.addInputUris(listOf(uriA2), hashesOf = mapOf(uriA2 to "hash-a"), maxImages = 10)

        assertEquals(listOf(uriA1), viewModel.composer.value.inputUris)
        assertEquals(AddInputResult(capped = true), result)
    }

    @Test
    fun addInputUris_keepsDistinctPhotosByHash() = runTest {
        viewModel.addInputUris(listOf(uriA1), hashesOf = mapOf(uriA1 to "hash-a"), maxImages = 10)

        viewModel.addInputUris(listOf(uriB), hashesOf = mapOf(uriB to "hash-b"), maxImages = 10)

        assertEquals(listOf(uriA1, uriB), viewModel.composer.value.inputUris)
    }

    @Test
    fun addInputUris_rejectsADuplicateWithinTheSameBatch() = runTest {
        val result = viewModel.addInputUris(
            listOf(uriA1, uriA2),
            hashesOf = mapOf(uriA1 to "hash-a", uriA2 to "hash-a"),
            maxImages = 10,
        )

        assertEquals(listOf(uriA1), viewModel.composer.value.inputUris)
        assertEquals(AddInputResult(capped = true), result)
    }

    @Test
    fun addInputUris_stillAddsAnUnhashableCandidate() = runTest {
        val result = viewModel.addInputUris(listOf(uriA1), hashesOf = emptyMap(), maxImages = 10)

        assertEquals(listOf(uriA1), viewModel.composer.value.inputUris)
        assertEquals(AddInputResult(capped = false), result)
    }

    @Test
    fun removeInputUri_forgetsTheHashSoTheSamePhotoCanBeReAdded() = runTest {
        viewModel.addInputUris(listOf(uriA1), hashesOf = mapOf(uriA1 to "hash-a"), maxImages = 10)
        viewModel.removeInputUri(uriA1)

        val result = viewModel.addInputUris(listOf(uriA2), hashesOf = mapOf(uriA2 to "hash-a"), maxImages = 10)

        assertEquals(listOf(uriA2), viewModel.composer.value.inputUris)
        assertEquals(AddInputResult(capped = false), result)
    }
}
