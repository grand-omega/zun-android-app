package dev.zun.flux.ui.gallery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.api.PromptDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.util.saveToPictures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

class GalleryViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val allJobs: StateFlow<List<JobSummaryDto>> =
        repository.getJobsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val prompts: StateFlow<List<PromptDto>> =
        repository.promptsState
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _tagFilter = MutableStateFlow<TagFilter>(TagFilter.All)
    val tagFilter: StateFlow<TagFilter> = _tagFilter.asStateFlow()

    /** Jobs visible to the user — already filtered by [tagFilter]. */
    val jobs: StateFlow<List<JobSummaryDto>> =
        combine(allJobs, _tagFilter) { all, filter ->
            when (filter) {
                TagFilter.All -> all
                is TagFilter.ByPromptId -> all.filter { it.prompt_id == filter.promptId }
                TagFilter.Custom -> all.filter { it.prompt_id == null && it.prompt_text != null }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Tag choices to show in the filter dropdown. */
    val availableTags: StateFlow<List<TagOption>> =
        combine(allJobs, prompts) { all, ps ->
            buildList {
                add(TagOption(TagFilter.All, "All", all.size))
                all.asSequence()
                    .mapNotNull { it.prompt_id }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .forEach { (id, count) ->
                        val label = ps.firstOrNull { it.id == id }?.label ?: "Unknown prompt"
                        add(TagOption(TagFilter.ByPromptId(id), label, count))
                    }
                val customCount = all.count { it.prompt_id == null && it.prompt_text != null }
                if (customCount > 0) {
                    add(TagOption(TagFilter.Custom, "Custom prompts", customCount))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setTagFilter(filter: TagFilter) {
        _tagFilter.value = filter
    }

    val isSelectionMode: StateFlow<Boolean> =
        _selectedIds.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _eventMessage = MutableStateFlow<String?>(null)
    val eventMessage: StateFlow<String?> = _eventMessage.asStateFlow()

    /** Non-empty while a soft-delete snackbar is showing. */
    private val _pendingUndo = MutableStateFlow<Set<String>?>(null)
    val pendingUndo: StateFlow<Set<String>?> = _pendingUndo.asStateFlow()

    /** Non-null when a "remove from app after save?" dialog should show. */
    private val _postSaveDelete = MutableStateFlow<Set<String>?>(null)
    val postSaveDelete: StateFlow<Set<String>?> = _postSaveDelete.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.syncHistory()
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
                    repository.deleteJob(id)
                } catch (_: Throwable) {
                }
            }
            clearSelection()
            _pendingUndo.value = ids
        }
    }

    fun undoDelete(ids: Set<String>) {
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    repository.restoreJob(id)
                } catch (_: Throwable) {
                }
            }
            _pendingUndo.value = null
            _eventMessage.value = "Restored ${ids.size} generation${if (ids.size == 1) "" else "s"}"
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
            ids.forEach { id ->
                try {
                    val model = repository.resultModel(id) ?: return@forEach
                    saveToPictures(context, model, "flux-$id.jpg")
                    savedIds += id
                } catch (_: Throwable) {
                }
            }
            clearSelection()
            _isSaving.value = false
            if (savedIds.isNotEmpty()) {
                _postSaveDelete.value = savedIds
            } else {
                _eventMessage.value = "No images saved"
            }
        }
    }

    fun confirmPostSaveDelete() {
        val ids = _postSaveDelete.value ?: return
        _postSaveDelete.value = null
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    repository.deleteJob(id)
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
                repository.deleteJob(jobId)
            } catch (_: Throwable) {
            }
        }
    }
}
