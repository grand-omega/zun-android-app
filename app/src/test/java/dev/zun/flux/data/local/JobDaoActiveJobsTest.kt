package dev.zun.flux.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Coverage for [JobDao.getActiveJobs], the source of truth behind the
 * "return to running batch" entry point on Home: it must include jobs still
 * processing and exclude anything terminal or locally deleted.
 */
@RunWith(RobolectricTestRunner::class)
class JobDaoActiveJobsTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: JobDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.jobDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun job(id: String, status: String, createdAt: Long = 1000) = JobEntity(
        id = id,
        status = status,
        inputId = null,
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
    )

    @Test
    fun `includes queued and running jobs`() = runBlocking {
        dao.insertJob(job("a", status = "queued"))
        dao.insertJob(job("b", status = "running"))

        assertEquals(listOf("a", "b"), dao.getActiveJobs().first().map { it.id })
    }

    @Test
    fun `excludes done, failed, and cancelled jobs`() = runBlocking {
        dao.insertJob(job("a", status = "queued"))
        dao.insertJob(job("b", status = "done"))
        dao.insertJob(job("c", status = "failed"))
        dao.insertJob(job("d", status = "cancelled"))

        assertEquals(listOf("a"), dao.getActiveJobs().first().map { it.id })
    }

    @Test
    fun `excludes jobs pending local delete regardless of status`() = runBlocking {
        dao.insertJob(job("a", status = "queued"))
        dao.insertJob(job("b", status = "running"))
        dao.insertPendingDelete(PendingDeleteEntity(jobId = "b", createdAt = 1000))

        assertEquals(listOf("a"), dao.getActiveJobs().first().map { it.id })
    }

    @Test
    fun `shrinks as jobs individually reach a terminal state, and empties once all do`() = runBlocking {
        dao.insertJob(job("a", status = "queued"))
        dao.insertJob(job("b", status = "running"))
        dao.insertJob(job("c", status = "queued"))
        assertEquals(listOf("a", "b", "c"), dao.getActiveJobs().first().map { it.id })

        dao.insertJob(job("a", status = "done"))
        assertEquals(listOf("b", "c"), dao.getActiveJobs().first().map { it.id })

        dao.insertJob(job("b", status = "failed"))
        assertEquals(listOf("c"), dao.getActiveJobs().first().map { it.id })

        dao.insertJob(job("c", status = "cancelled"))
        assertEquals(emptyList<String>(), dao.getActiveJobs().first().map { it.id })
    }
}
