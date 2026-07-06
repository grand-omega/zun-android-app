package dev.zun.flux.data.local

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Proves [JobDao.getActiveJobs] is backed by durable storage, not in-memory
 * state: closing the database and reopening a fresh instance against the
 * same file must still see the same active jobs. This is what makes it
 * possible to return to a running batch after the app has been fully closed
 * and reopened (spec.md User Story 3 / FR-005), not just backgrounded.
 */
@RunWith(RobolectricTestRunner::class)
class JobDaoPersistenceTest {

    private lateinit var dbFile: File

    private fun openDatabase(): AppDatabase {
        val context = RuntimeEnvironment.getApplication()
        return Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
    }

    private fun job(id: String, status: String) = JobEntity(
        id = id,
        status = status,
        inputId = null,
        promptId = null,
        promptText = null,
        workflow = null,
        seed = null,
        progress = null,
        error = null,
        createdAt = 1000,
        startedAt = null,
        completedAt = null,
        durationSeconds = null,
        width = null,
        height = null,
    )

    @After
    fun tearDown() {
        if (::dbFile.isInitialized) dbFile.delete()
    }

    @Test
    fun `active jobs survive closing and reopening the database`() = runBlocking {
        dbFile = File.createTempFile("job-persistence-test", ".db")

        val firstInstance = openDatabase()
        firstInstance.jobDao().insertJobs(
            listOf(job("a", status = "queued"), job("b", status = "running"), job("c", status = "done")),
        )
        firstInstance.close()

        val secondInstance = openDatabase()
        try {
            val activeIds = secondInstance.jobDao().getActiveJobs().first().map { it.id }
            assertEquals(listOf("a", "b"), activeIds)
        } finally {
            secondInstance.close()
        }
    }
}
