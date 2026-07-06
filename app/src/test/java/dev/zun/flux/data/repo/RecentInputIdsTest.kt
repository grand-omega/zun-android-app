package dev.zun.flux.data.repo

import dev.zun.flux.data.local.JobEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentInputIdsTest {
    @Test
    fun dedupeRecentInputIds_collapsesRepeatUploadsOfTheSamePhoto() {
        // Same source photo picked twice ends up as two jobs with different
        // server-assigned inputIds but the same sourceSha256.
        val entities = listOf(
            testJob(id = "job-2", inputId = 20, sourceSha256 = "hash-a", createdAt = 200),
            testJob(id = "job-1", inputId = 10, sourceSha256 = "hash-a", createdAt = 100),
        )

        assertEquals(listOf(20), dedupeRecentInputIds(entities, limit = 3))
    }

    @Test
    fun dedupeRecentInputIds_keepsDistinctPhotos() {
        val entities = listOf(
            testJob(id = "job-2", inputId = 20, sourceSha256 = "hash-b", createdAt = 200),
            testJob(id = "job-1", inputId = 10, sourceSha256 = "hash-a", createdAt = 100),
        )

        assertEquals(listOf(20, 10), dedupeRecentInputIds(entities, limit = 3))
    }

    @Test
    fun dedupeRecentInputIds_fallsBackToInputIdWhenHashIsMissing() {
        // Legacy jobs that predate hash tracking must not all collapse into
        // a single entry just because sourceSha256 is null for all of them.
        val entities = listOf(
            testJob(id = "job-2", inputId = 20, sourceSha256 = null, createdAt = 200),
            testJob(id = "job-1", inputId = 10, sourceSha256 = null, createdAt = 100),
        )

        assertEquals(listOf(20, 10), dedupeRecentInputIds(entities, limit = 3))
    }

    @Test
    fun dedupeRecentInputIds_skipsJobsWithNoInputId() {
        val entities = listOf(
            testJob(id = "job-1", inputId = null, sourceSha256 = "hash-a", createdAt = 100),
        )

        assertEquals(emptyList<Int>(), dedupeRecentInputIds(entities, limit = 3))
    }

    @Test
    fun dedupeRecentInputIds_respectsLimit() {
        val entities = listOf(
            testJob(id = "job-3", inputId = 30, sourceSha256 = "hash-c", createdAt = 300),
            testJob(id = "job-2", inputId = 20, sourceSha256 = "hash-b", createdAt = 200),
            testJob(id = "job-1", inputId = 10, sourceSha256 = "hash-a", createdAt = 100),
        )

        assertEquals(listOf(30, 20), dedupeRecentInputIds(entities, limit = 2))
    }

    private fun testJob(
        id: String,
        inputId: Int?,
        sourceSha256: String?,
        createdAt: Long,
    ): JobEntity = JobEntity(
        id = id,
        status = "done",
        inputId = inputId,
        promptId = null,
        promptText = null,
        workflow = null,
        seed = null,
        progress = null,
        error = null,
        createdAt = createdAt,
        startedAt = null,
        completedAt = null,
        durationSeconds = null,
        width = null,
        height = null,
        sourceSha256 = sourceSha256,
        resultSha256 = null,
        lineageRootId = null,
    )
}
