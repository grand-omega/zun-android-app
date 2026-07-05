package dev.zun.flux.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the actual SQL for the new lineage-tracking queries (feature
 * 004) against a real in-memory Room database — the queries themselves
 * were never covered by [AppDatabaseMigrationTest], which only checks the
 * schema migration, not query correctness.
 */
@RunWith(AndroidJUnit4::class)
class JobDaoLineageTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: JobDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.jobDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findDoneJobByHash_matchesOnSourceOrResultHash_onlyWhenDone() = runBlocking {
        dao.insertJob(job(id = "done-source", status = "done", sourceSha256 = "hash-a"))
        dao.insertJob(job(id = "done-result", status = "done", resultSha256 = "hash-b"))
        dao.insertJob(job(id = "queued-source", status = "queued", sourceSha256 = "hash-c"))

        assertEquals("done-source", dao.findDoneJobByHash("hash-a")?.id)
        assertEquals("done-result", dao.findDoneJobByHash("hash-b")?.id)
        assertNull("a queued job's hash must not match", dao.findDoneJobByHash("hash-c"))
        assertNull("no job has this hash", dao.findDoneJobByHash("hash-z"))
    }

    @Test
    fun updateSourceLineage_setsHashAndRootWithoutTouchingOtherColumns() = runBlocking {
        dao.insertJob(job(id = "job-1", status = "queued", progress = 0.5f))

        dao.updateSourceLineage("job-1", hash = "hash-x", rootId = "job-1")

        val updated = dao.getJobById("job-1")
        assertEquals("hash-x", updated?.sourceSha256)
        assertEquals("job-1", updated?.lineageRootId)
        assertEquals("queued", updated?.status)
        assertEquals(0.5f, updated?.progress)
    }

    @Test
    fun updateResultHash_setsOnlyResultHash() = runBlocking {
        dao.insertJob(job(id = "job-1", status = "done", sourceSha256 = "hash-src"))

        dao.updateResultHash("job-1", "hash-result")

        val updated = dao.getJobById("job-1")
        assertEquals("hash-src", updated?.sourceSha256)
        assertEquals("hash-result", updated?.resultSha256)
    }

    @Test
    fun getJobsByLineageRoot_returnsOnlyDoneJobsInThatGroup_orderedByCreatedAt() = runBlocking {
        dao.insertJob(job(id = "root", status = "done", lineageRootId = "root", createdAt = 100))
        dao.insertJob(job(id = "child-1", status = "done", lineageRootId = "root", createdAt = 300))
        dao.insertJob(job(id = "child-2", status = "done", lineageRootId = "root", createdAt = 200))
        dao.insertJob(job(id = "other-root", status = "done", lineageRootId = "other", createdAt = 150))
        dao.insertJob(job(id = "failed-child", status = "failed", lineageRootId = "root", createdAt = 250))

        val entries = dao.getJobsByLineageRoot("root").first()

        assertEquals(listOf("root", "child-2", "child-1"), entries.map { it.id })
    }

    @Test
    fun countByLineageRoot_countsOnlyDoneJobsInThatGroup() = runBlocking {
        dao.insertJob(job(id = "root", status = "done", lineageRootId = "root"))
        dao.insertJob(job(id = "child", status = "done", lineageRootId = "root"))
        dao.insertJob(job(id = "failed", status = "failed", lineageRootId = "root"))
        dao.insertJob(job(id = "other", status = "done", lineageRootId = "other"))

        assertEquals(2, dao.countByLineageRoot("root"))
        assertEquals(1, dao.countByLineageRoot("other"))
        assertEquals(0, dao.countByLineageRoot("nonexistent"))
    }

    @Test
    fun hardDeletingAJob_removesItFromFutureHashDetection() = runBlocking {
        dao.insertJob(job(id = "job-1", status = "done", sourceSha256 = "hash-a", lineageRootId = "job-1"))
        assertEquals("job-1", dao.findDoneJobByHash("hash-a")?.id)

        dao.deleteJobById("job-1")

        assertNull(
            "a hard-deleted job's hash must no longer be detectable — no separate index outlives the row (FR-009)",
            dao.findDoneJobByHash("hash-a"),
        )
    }

    @Test
    fun getJobsByIds_returnsOnlyMatchingRows() = runBlocking {
        dao.insertJob(job(id = "a", status = "done"))
        dao.insertJob(job(id = "b", status = "done"))
        dao.insertJob(job(id = "c", status = "done"))

        val result = dao.getJobsByIds(listOf("a", "c", "does-not-exist"))

        assertEquals(setOf("a", "c"), result.map { it.id }.toSet())
    }

    private fun job(
        id: String,
        status: String,
        sourceSha256: String? = null,
        resultSha256: String? = null,
        lineageRootId: String? = null,
        progress: Float? = null,
        createdAt: Long = 0L,
    ): JobEntity = JobEntity(
        id = id,
        status = status,
        inputId = null,
        promptId = null,
        promptText = null,
        workflow = null,
        seed = null,
        progress = progress,
        error = null,
        createdAt = createdAt,
        startedAt = null,
        completedAt = null,
        durationSeconds = null,
        width = null,
        height = null,
        sourceSha256 = sourceSha256,
        resultSha256 = resultSha256,
        lineageRootId = lineageRootId,
    )
}
