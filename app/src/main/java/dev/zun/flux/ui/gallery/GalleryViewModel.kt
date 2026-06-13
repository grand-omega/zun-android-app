package dev.zun.flux.ui.gallery

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import androidx.paging.map
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.api.effectivePromptId
import dev.zun.flux.data.repo.ImageSourceRepository
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.data.repo.PromptRepository
import dev.zun.flux.util.formatTimestamp
import dev.zun.flux.util.saveToPictures
import dev.zun.flux.util.shareImages
import dev.zun.flux.util.toUserMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the user is filtering the gallery by. */
sealed interface TagFilter {
    data object All : TagFilter
    data class ByPromptId(val promptId: Long) : TagFilter
    data object Custom : TagFilter
}

/** A choice in the tag-filter dropdown. */
data class TagOption(
    val filter: TagFilter,
    val label: String,
    val count: Int,
)

/** A row in the paged gallery grid: either a job tile or a date header. */
sealed interface GalleryGridItem {
    data class JobItem(val job: JobSummaryDto) : GalleryGridItem
    data class DateSeparator(val date: String) : GalleryGridItem
}

class GalleryViewModel(
    private val jobRepo: JobRepository,
    private val promptRepo: PromptRepository,
    private val imageRepo: ImageSourceRepository,
) : ViewModel() {
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val allJobs: StateFlow<List<JobSummaryDto>> =
        jobRepo.getJobsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val prompts: StateFlow<List<PromptDto>> =
        promptRepo.promptsState
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _tagFilter = MutableStateFlow<TagFilter>(TagFilter.All)
    val tagFilter: StateFlow<TagFilter> = _tagFilter.asStateFlow()

    /** Free-text search over prompt labels and custom prompt text. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private fun JobSummaryDto.matchesQuery(query: String, prompts: List<PromptDto>): Boolean {
        val label = effectivePromptId?.let { id -> prompts.firstOrNull { it.id == id }?.label }
        return label?.contains(query, ignoreCase = true) == true ||
            prompt_text?.contains(query, ignoreCase = true) == true
    }

    /**
     * Filtered list view of all done jobs. Used by PhotoViewerScreen
     * (HorizontalPager). Grid uses [pagedGridItems] instead.
     */
    val jobs: StateFlow<List<JobSummaryDto>> =
        combine(allJobs, _tagFilter, _searchQuery, prompts) { all, filter, query, ps ->
            val tagFiltered = when (filter) {
                TagFilter.All -> all
                is TagFilter.ByPromptId -> all.filter { it.effectivePromptId == filter.promptId }
                TagFilter.Custom -> all.filter { it.effectivePromptId == null && it.prompt_text != null }
            }
            val trimmed = query.trim()
            if (trimmed.isBlank()) tagFiltered else tagFiltered.filter { it.matchesQuery(trimmed, ps) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Paged stream for the gallery grid, with date-separator rows inserted
     * between groups. Reacts to [tagFilter] and [searchQuery] changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedGridItems: Flow<PagingData<GalleryGridItem>> =
        combine(_tagFilter, _searchQuery, prompts) { filter, query, ps -> Triple(filter, query.trim(), ps) }
            .flatMapLatest { (filter, query, ps) ->
                val (promptId, customOnly) = when (filter) {
                    TagFilter.All -> null to false
                    is TagFilter.ByPromptId -> filter.promptId to false
                    TagFilter.Custom -> null to true
                }
                jobRepo.pagedJobs(promptId, customOnly).map { pagingData ->
                    if (query.isBlank()) {
                        pagingData
                    } else {
                        // Prompt list is small and already in memory; filtering the
                        // Room-backed pages here avoids duplicating labels into SQL.
                        pagingData.filter { it.matchesQuery(query, ps) }
                    }
                }
            }.map { pagingData ->
                pagingData
                    .map<JobSummaryDto, GalleryGridItem> { GalleryGridItem.JobItem(it) }
                    .insertSeparators { before, after ->
                        val afterJob = (after as? GalleryGridItem.JobItem)?.job ?: return@insertSeparators null
                        val afterDate = formatTimestamp(afterJob.created_at)
                        val beforeJob = (before as? GalleryGridItem.JobItem)?.job
                        if (beforeJob == null || formatTimestamp(beforeJob.created_at) != afterDate) {
                            GalleryGridItem.DateSeparator(afterDate)
                        } else {
                            null
                        }
                    }
            }.cachedIn(viewModelScope)

    /** Tag choices to show in the filter dropdown. Counts come from SQL aggregates. */
    val availableTags: StateFlow<List<TagOption>> =
        combine(jobRepo.jobTagStats(), prompts) { stats, ps ->
            buildList {
                add(TagOption(TagFilter.All, "All", stats.totalCount))
                stats.perPromptCounts.entries
                    .sortedByDescending { it.value }
                    .forEach { (id, count) ->
                        val label = ps.firstOrNull { it.id == id }?.label ?: "Unknown prompt"
                        add(TagOption(TagFilter.ByPromptId(id), label, count))
                    }
                if (stats.customCount > 0) {
                    add(TagOption(TagFilter.Custom, "Custom prompts", stats.customCount))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setTagFilter(filter: TagFilter) {
        _tagFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val isSelectionMode: StateFlow<Boolean> =
        _selectedIds.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _eventMessage = MutableStateFlow<String?>(null)
    val eventMessage: StateFlow<String?> = _eventMessage.asStateFlow()

    /** Non-empty while a soft-delete snackbar is showing. */
    private val _pendingUndo = MutableStateFlow<Set<String>?>(null)
    val pendingUndo: StateFlow<Set<String>?> = _pendingUndo.asStateFlow()

    /**
     * Job the photo viewer is currently showing. The grid scrolls its tile
     * into view so the shared-element return transition has a target even
     * after the user paged away from the tile they opened.
     */
    private val _viewerJobId = MutableStateFlow<String?>(null)
    val viewerJobId: StateFlow<String?> = _viewerJobId.asStateFlow()

    fun setViewerJob(jobId: String?) {
        _viewerJobId.value = jobId
    }

    /** Non-null when a "remove from app after save?" dialog should show. */
    private val _postSaveDelete = MutableStateFlow<Set<String>?>(null)
    val postSaveDelete: StateFlow<Set<String>?> = _postSaveDelete.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            jobRepo.syncHistory()
            _isLoading.value = false
        }
    }

    fun toggleSelection(jobId: String) {
        val current = _selectedIds.value
        if (current.contains(jobId)) {
            _selectedIds.value = current - jobId
        } else {
            _selectedIds.value = current + jobId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Replaces the entire selection — used by drag-select to apply a range
     *  computed from anchor + cursor in one shot. */
    fun setSelection(ids: Set<String>) {
        _selectedIds.value = ids
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    jobRepo.deleteJob(id)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to delete job id=$id", t)
                }
            }
            clearSelection()
            _pendingUndo.value = ids
        }
    }

    fun undoDelete(ids: Set<String>) {
        viewModelScope.launch {
            val restoredIds = mutableSetOf<String>()
            ids.forEach { id ->
                try {
                    jobRepo.restoreJob(id)
                    restoredIds += id
                } catch (t: Throwable) {
                    _eventMessage.value = t.toUserMessage("restore")
                }
            }
            _pendingUndo.value = null
            if (restoredIds.isNotEmpty()) {
                _eventMessage.value = "Restored ${restoredIds.size} generation${if (restoredIds.size == 1) "" else "s"}"
            }
        }
    }

    fun clearPendingUndo() {
        _pendingUndo.value = null
    }

    fun saveSelected(context: Context) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return

        viewModelScope.launch {
            _isSaving.value = true
            val savedIds = mutableSetOf<String>()
            var failures = 0
            ids.forEach { id ->
                try {
                    val model = imageRepo.resultModel(id) ?: return@forEach
                    saveToPictures(context, model, "flux-$id.jpg")
                    savedIds += id
                } catch (_: Throwable) {
                    failures++
                }
            }
            clearSelection()
            _isSaving.value = false
            if (savedIds.isNotEmpty()) {
                _postSaveDelete.value = savedIds
            } else {
                _eventMessage.value = if (failures > 0) {
                    "Save failed. Connect to the server for uncached originals."
                } else {
                    "No images saved"
                }
            }
        }
    }

    fun shareSelected(context: Context) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return

        viewModelScope.launch {
            _isSharing.value = true
            try {
                val models = ids.mapNotNull { id -> imageRepo.resultModel(id) }
                if (models.isNotEmpty()) {
                    shareImages(context, models)
                    clearSelection()
                } else {
                    _eventMessage.value = "No images to share"
                }
            } catch (t: Throwable) {
                _eventMessage.value = "Share failed. Connect to the server for uncached originals."
            } finally {
                _isSharing.value = false
            }
        }
    }

    fun confirmPostSaveDelete() {
        val ids = _postSaveDelete.value ?: return
        _postSaveDelete.value = null
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    jobRepo.deleteJob(id)
                } catch (_: Throwable) {
                }
            }
            _pendingUndo.value = ids
        }
    }

    fun dismissPostSaveDelete() {
        _postSaveDelete.value = null
    }

    fun clearEventMessage() {
        _eventMessage.value = null
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            try {
                jobRepo.deleteJob(jobId)
                _pendingUndo.value = setOf(jobId)
            } catch (_: Throwable) {
            }
        }
    }

    private companion object {
        const val TAG = "GalleryViewModel"
    }
}
