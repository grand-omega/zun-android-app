package dev.zun.flux.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zun.flux.data.api.JobSummaryDto
import dev.zun.flux.data.repo.JobRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class EditHistoryViewModel(
    lineageRootId: String,
    jobs: JobRepository,
) : ViewModel() {
    val entries: StateFlow<List<JobSummaryDto>> = jobs.getJobsByLineageRoot(lineageRootId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )
}
