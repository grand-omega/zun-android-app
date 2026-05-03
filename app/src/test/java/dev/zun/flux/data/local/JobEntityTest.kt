package dev.zun.flux.data.local

import dev.zun.flux.data.api.JobStatusDto
import dev.zun.flux.data.api.JobSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JobEntityTest {
    @Test
    fun statusDtoRoundTrip_preservesJobFields() {
        val dto = JobStatusDto(
            id = "job-1",
            status = "running",
            input_id = 42,
            prompt_id = 7,
            prompt_text = null,
            workflow = "flux2_klein_edit",
            seed = 1234,
            progress = 0.5f,
            error = null,
            created_at = 100,
            started_at = 110,
            completed_at = null,
            width = 1024,
            height = 768,
        )

        val roundTripped = dto.toEntity().toStatusDto()

        assertEquals(dto, roundTripped)
    }

    @Test
    fun summaryEntity_usesDurationWhenCompletedAtIsMissing() {
        val dto = JobSummaryDto(
            id = "job-2",
            status = "done",
            input_id = 8,
            prompt_id = null,
            prompt_text = "make it sharper",
            workflow = "flux2_klein_edit",
            seed = null,
            created_at = 100,
            completed_at = null,
            duration_seconds = 12,
        )

        val entity = dto.toEntity()

        assertEquals(112L, entity.completedAt)
        assertEquals(12, entity.durationSeconds)
        assertEquals(1f, entity.progress ?: 0f, 0f)
    }

    @Test
    fun summaryEntity_keepsNullCompletedAtWhenDurationIsMissing() {
        val dto = JobSummaryDto(
            id = "job-3",
            created_at = 100,
            completed_at = null,
            duration_seconds = null,
        )

        val entity = dto.toEntity()

        assertNull(entity.completedAt)
        assertNull(entity.durationSeconds)
    }
}
