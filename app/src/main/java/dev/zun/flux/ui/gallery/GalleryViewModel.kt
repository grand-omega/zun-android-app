package dev.zun.flux.ui.gallery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.JobRepository
import dev.zun.flux.util.saveToPictures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val jobs: StateFlow<List<JobSummaryDto>> =
        repository.getJobsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isSelectionMode: StateFlow<Boolean> =
        _selectedIds.map { it.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _eventMessage = MutableStateFlow<String?>(null)
    val eventMessage: StateFlow<String?> = _eventMessage.asStateFlow()

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

    fun deleteSelected() {
        val ids = _selectedIds.value
        viewModelScope.launch {
            ids.forEach { id ->
                try {
                    repository.deleteJob(id)
                } catch (_: Throwable) {
                }
            }
            clearSelection()
        }
    }

    fun saveSelected(context: Context) {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return

        viewModelScope.launch {
            _isSaving.value = true
            var savedCount = 0
            ids.forEach { id ->
                try {
                    val model = repository.resultModel(id) ?: return@forEach
                    saveToPictures(context, model, "flux-$id.jpg")
                    savedCount++
                } catch (_: Throwable) {
                }
            }
            clearSelection()
            _isSaving.value = false
            _eventMessage.value = "Saved $savedCount images to Pictures"
        }
    }

    fun clearEventMessage() {
        _eventMessage.value = null
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            try {
                repository.deleteJob(jobId)
            } catch (_: Throwable) {
                // Error handling handled by UI if needed
            }
        }
    }
}
