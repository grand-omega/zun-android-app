package dev.zun.flux.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: JobRepository,
) : ViewModel() {
    private val _jobs = MutableStateFlow<List<JobSummaryDto>>(emptyList())
    val jobs: StateFlow<List<JobSummaryDto>> = _jobs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _jobs.value =
                try {
                    repository.listJobs(status = "done", limit = 100)
                } catch (_: Throwable) {
                    emptyList()
                }
            _isLoading.value = false
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            try {
                repository.deleteJob(jobId)
                _jobs.value = _jobs.value.filter { it.id != jobId }
            } catch (_: Throwable) {
                // Handle error
            }
        }
    }
}
