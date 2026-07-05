package dev.zun.flux.data.repo

import dev.zun.flux.data.local.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LineageAssignmentTest {
    @Test
    fun assignLineageRoot_startsNewRootWhenNoMatch() {
        assertEquals("new-job", assignLineageRoot("new-job", match = null))
    }

    @Test
    fun assignLineageRoot_inheritsMatchsExistingRoot() {
        val match = testJob(id = "job-2", lineageRootId = "job-1")
        assertEquals("job-1", assignLineageRoot("job-3", match))
    }

    @Test
    fun assignLineageRoot_fallsBackToMatchsOwnIdWhenLegacy() {
        val match = testJob(id = "job-1", lineageRootId = null)
        assertEquals("job-1", assignLineageRoot("job-2", match))
    }

    private fun testJob(id: String, lineageRootId: String?): JobEntity = JobEntity(
        id = id,
        status = "done",
        inputId = null,
        promptId = null,
        promptText = null,
        workflow = null,
        seed = null,
        progress = null,
        error = null,
        createdAt = 0L,
        startedAt = null,
        completedAt = null,
        durationSeconds = null,
        width = null,
        height = null,
        sourceSha256 = null,
        resultSha256 = null,
        lineageRootId = lineageRootId,
    )
}
