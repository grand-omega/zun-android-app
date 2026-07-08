package dev.zun.flux.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.OfflineImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CacheCleanupState(
    val entries: List<OfflineImageCache.CachedJobSummary> = emptyList(),
    val isLoading: Boolean = true,
    /** Set only when the confirm step was blocked by the connectivity re-check (FR-010). */
    val blockedMessage: String? = null,
)

class CacheCleanupViewModel(private val images: ImageSourceRepository) : ViewModel() {
    private val _state = MutableStateFlow(CacheCleanupState())
    val state: StateFlow<CacheCleanupState> = _state.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, blockedMessage = null)
            // listCachedJobs() walks the whole cache dir -- keep it off the main thread. Falls
            // back to empty on a filesystem error (e.g. storage unmounted) rather than crashing.
            val entries = withContext(Dispatchers.IO) {
                runCatching { images.listCachedJobs() }.getOrDefault(emptyList())
            }
            _state.value = CacheCleanupState(entries = entries, isLoading = false)
        }
    }

    /**
     * Evicts every previewed entry, but only after re-checking connectivity right now (research.md
     * Decision 3) -- a state change since the preview was shown (e.g. going offline) must not
     * result in evicting an image's only reachable copy.
     */
    fun confirmClear() {
        viewModelScope.launch {
            if (!images.hasNetworkConnectivity()) {
                _state.value = _state.value.copy(blockedMessage = BLOCKED_MESSAGE)
                return@launch
            }
            val entries = _state.value.entries
            withContext(Dispatchers.IO) {
                entries.forEach { images.evictFromCache(it.jobId) }
            }
            refresh()
        }
    }

    private companion object {
        const val BLOCKED_MESSAGE = "Needs a network connection -- try again once you're back online."
    }
}
