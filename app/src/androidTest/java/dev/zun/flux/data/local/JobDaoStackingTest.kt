package dev.zun.flux.data.local

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real SQL for feature 009's variant-stacking queries against an in-memory Room
 * database. Grouping/counting happens in SQL (not client-side, post-paging) specifically so a
 * stack can never split across a Paging 3 page boundary — see research.md Decision 1.
 */
@RunWith(AndroidJUnit4::class)
class JobDaoStackingTest {
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

    private suspend fun loadAll(favoritesOnly: Boolean = false): List<JobWithStackCount> {
        val page = dao.pagedDoneJobsAll(newestFirst = true, favoritesOnly = favoritesOnly).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        return page.data
    }

    @Test
    fun threeVariantsSharingALineageRoot_collapseToOneRowWithCountThree() = runBlocking {
        dao.insertJob(job(id = "v1", lineageRootId = "root", createdAt = 100))
        dao.insertJob(job(id = "v2", lineageRootId = "root", createdAt = 200))
        dao.insertJob(job(id = "v3", lineageRootId = "root", createdAt = 300))

        val rows = loadAll()

        assertEquals(1, rows.size)
        assertEquals("v3", rows.single().job.id)
        assertEquals(3, rows.single().stackCount)
    }

    @Test
    fun aJobWithNoLineageSiblings_isItsOwnStackOfOne() = runBlocking {
        dao.insertJob(job(id = "solo", lineageRootId = "solo", createdAt = 100))

        val rows = loadAll()

        assertEquals(1, rows.size)
        assertEquals("solo", rows.single().job.id)
        assertEquals(1, rows.single().stackCount)
    }

    @Test
    fun aJobWithNoLineageRootIdAtAll_fallsBackToGroupingByItsOwnId() = runBlocking {
        // Never went through recordSourceLineage (best-effort failure, or a legacy row) --
        // lineageRootId is null, so COALESCE(lineageRootId, id) must fall back to the job's own id.
        dao.insertJob(job(id = "legacy", lineageRootId = null, createdAt = 100))

        val rows = loadAll()

        assertEquals(1, rows.size)
        assertEquals("legacy", rows.single().job.id)
        assertEquals(1, rows.single().stackCount)
    }

    @Test
    fun pendingDeleteAndNonDoneSiblings_areExcludedFromTheStackAndItsCount() = runBlocking {
        dao.insertJob(job(id = "v1", lineageRootId = "root", createdAt = 100))
        dao.insertJob(job(id = "v2", lineageRootId = "root", createdAt = 200))
        dao.insertJob(job(id = "v3-deleted", lineageRootId = "root", createdAt = 300))
        dao.insertPendingDelete(PendingDeleteEntity(jobId = "v3-deleted", createdAt = 0))
        dao.insertJob(job(id = "v4-queued", lineageRootId = "root", status = "queued", createdAt = 400))

        val rows = loadAll()

        assertEquals(1, rows.size)
        assertEquals("v2", rows.single().job.id)
        assertEquals(2, rows.single().stackCount)
    }

    @Test
    fun favoritesOnly_narrowsTheCountToJustTheFavoritedSiblings() = runBlocking {
        dao.insertJob(job(id = "v1", lineageRootId = "root", createdAt = 100, isFavorite = true))
        dao.insertJob(job(id = "v2", lineageRootId = "root", createdAt = 200, isFavorite = false))
        dao.insertJob(job(id = "v3", lineageRootId = "root", createdAt = 300, isFavorite = true))

        val rows = loadAll(favoritesOnly = true)

        assertEquals(1, rows.size)
        // Cover row is the most-recently-created among the FAVORITED siblings (v1, v3), not v2.
        assertEquals("v3", rows.single().job.id)
        assertEquals(2, rows.single().stackCount)
    }

    @Test
    fun stackHasFavorite_isTrueWhenANonCoverSiblingIsFavoritedEvenIfTheCoverIsNot() = runBlocking {
        dao.insertJob(job(id = "v1", lineageRootId = "root", createdAt = 100, isFavorite = true))
        dao.insertJob(job(id = "v2-cover", lineageRootId = "root", createdAt = 200, isFavorite = false))

        val rows = loadAll()

        assertEquals(1, rows.size)
        assertEquals("v2-cover", rows.single().job.id)
        assertEquals(false, rows.single().job.isFavorite)
        assertEquals(true, rows.single().stackHasFavorite)
    }

    @Test
    fun stackHasFavorite_isFalseWhenNoMemberOfTheStackIsFavorited() = runBlocking {
        dao.insertJob(job(id = "v1", lineageRootId = "root", createdAt = 100, isFavorite = false))
        dao.insertJob(job(id = "v2", lineageRootId = "root", createdAt = 200, isFavorite = false))

        val rows = loadAll()

        assertEquals(1, rows.size)
        assertEquals(false, rows.single().stackHasFavorite)
    }

    @Test
    fun stackHasFavorite_isTrueWhenTheCoverItselfIsTheFavoritedOne() = runBlocking {
        dao.insertJob(job(id = "v1", lineageRootId = "root", createdAt = 100, isFavorite = false))
        dao.insertJob(job(id = "v2-cover", lineageRootId = "root", createdAt = 200, isFavorite = true))

        val rows = loadAll()

        assertEquals(1, rows.size)
        assertEquals(true, rows.single().job.isFavorite)
        assertEquals(true, rows.single().stackHasFavorite)
    }

    @Test
    fun twoIndependentStacks_bothAppearAsSeparateRows() = runBlocking {
        dao.insertJob(job(id = "a1", lineageRootId = "root-a", createdAt = 100))
        dao.insertJob(job(id = "a2", lineageRootId = "root-a", createdAt = 200))
        dao.insertJob(job(id = "b1", lineageRootId = "root-b", createdAt = 150))

        val rows = loadAll().associateBy { it.job.id }

        assertEquals(2, rows.size)
        assertEquals(2, rows.getValue("a2").stackCount)
        assertEquals(1, rows.getValue("b1").stackCount)
    }

    private fun job(
        id: String,
        lineageRootId: String?,
        status: String = "done",
        createdAt: Long = 0L,
        isFavorite: Boolean = false,
    ): JobEntity = JobEntity(
        id = id,
        status = status,
        inputId = null,
        promptId = null,
        promptText = null,
        workflow = null,
        seed = null,
        progress = if (status == "done") 1f else null,
        error = null,
        createdAt = createdAt,
        startedAt = null,
        completedAt = null,
        durationSeconds = null,
        width = null,
        height = null,
        sourceSha256 = null,
        resultSha256 = null,
        lineageRootId = lineageRootId,
        isFavorite = isFavorite,
    )
}
