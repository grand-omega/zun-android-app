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
 * Coverage for [JobDao.getFailedJobs], the source behind the "review failed generations" entry
 * point on Home. Before it existed a failed job had no surface at all: the gallery lists only
 * 'done', [JobDao.getActiveJobs] excludes terminal states, and syncHistory fetches only 'done'.
 */
@RunWith(RobolectricTestRunner::class)
class JobDaoFailedJobsTest {

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
    fun `includes failed jobs oldest first`() = runBlocking {
        dao.insertJob(job("b", status = "failed", createdAt = 2000))
        dao.insertJob(job("a", status = "failed", createdAt = 1000))

        assertEquals(listOf("a", "b"), dao.getFailedJobs().first().map { it.id })
    }

    @Test
    fun `excludes every non-failed status`() = runBlocking {
        dao.insertJob(job("queued", status = "queued"))
        dao.insertJob(job("running", status = "running"))
        dao.insertJob(job("done", status = "done"))
        // Cancelled is the user's own action — re-surfacing it would nag about something
        // they already know about, so it is excluded on purpose.
        dao.insertJob(job("cancelled", status = "cancelled"))

        assertEquals(emptyList<String>(), dao.getFailedJobs().first().map { it.id })
    }

    @Test
    fun `excludes locally deleted failures`() = runBlocking {
        dao.insertJob(job("a", status = "failed"))
        dao.insertJob(job("b", status = "failed"))
        dao.insertPendingDelete(PendingDeleteEntity(jobId = "a", createdAt = 0L))

        assertEquals(listOf("b"), dao.getFailedJobs().first().map { it.id })
    }

    @Test
    fun `a failed job is in neither the gallery feed nor the active banner`() = runBlocking {
        dao.insertJob(job("boom", status = "failed"))

        // The exact gap this feature closes: both existing surfaces skip it.
        assertEquals(emptyList<String>(), dao.getActiveJobs().first().map { it.id })
        assertEquals(emptyList<String>(), dao.getVisibleJobs().first().filter { it.status == "done" }.map { it.id })
        assertEquals(listOf("boom"), dao.getFailedJobs().first().map { it.id })
    }
}
