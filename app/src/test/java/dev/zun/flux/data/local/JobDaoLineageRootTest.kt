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
 * A lineage root is identified two different ways depending on where the job came from.
 * `assignLineageRoot` gives a locally created job `lineageRootId == its own id`, but a job first
 * seen through a server sync — or one that predates lineage tracking — carries null, and nothing
 * ever backfills it. The paged gallery queries handle both via
 * `COALESCE(lineageRootId, id)`; these two DAO methods used to match only the first, so a null
 * root was silently dropped from its own lineage.
 */
@RunWith(RobolectricTestRunner::class)
class JobDaoLineageRootTest {

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

    private fun job(id: String, lineageRootId: String?, createdAt: Long) = JobEntity(
        id = id,
        status = "done",
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
        lineageRootId = lineageRootId,
    )

    @Test
    fun `a synced root with a null lineageRootId is part of its own lineage`() = runBlocking {
        // The original arrived from the server, so it has no lineageRootId; the edit made on this
        // device points back at it. This is what "edit an older image once" produces.
        dao.insertJob(job("original", lineageRootId = null, createdAt = 1000))
        dao.insertJob(job("edit", lineageRootId = "original", createdAt = 2000))

        val stack = dao.getJobsByLineageRoot("original").first().map { it.id }

        // Before the fix this returned just [edit] — the stack badge counted 2 while the viewer
        // showed 1, and the image the user actually tapped was the one missing.
        assertEquals(listOf("original", "edit"), stack)
        assertEquals(2, dao.countByLineageRoot("original"))
    }

    @Test
    fun `a locally created root already carries its own id and still resolves`() = runBlocking {
        // assignLineageRoot(jobId, null) returns jobId, so local originals point at themselves.
        dao.insertJob(job("original", lineageRootId = "original", createdAt = 1000))
        dao.insertJob(job("edit", lineageRootId = "original", createdAt = 2000))

        assertEquals(listOf("original", "edit"), dao.getJobsByLineageRoot("original").first().map { it.id })
        assertEquals(2, dao.countByLineageRoot("original"))
    }

    @Test
    fun `an unrelated null-root job is not pulled into someone else's lineage`() = runBlocking {
        dao.insertJob(job("original", lineageRootId = null, createdAt = 1000))
        dao.insertJob(job("stranger", lineageRootId = null, createdAt = 1500))

        assertEquals(listOf("original"), dao.getJobsByLineageRoot("original").first().map { it.id })
        assertEquals(1, dao.countByLineageRoot("original"))
    }
}
