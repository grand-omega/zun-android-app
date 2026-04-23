package dev.zun.flux.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    val jobs: StateFlow<List<JobSummaryDto>> =
        repository.getJobsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
