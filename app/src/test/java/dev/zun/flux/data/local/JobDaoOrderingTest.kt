package dev.zun.flux.data.local

import androidx.paging.PagingSource
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
 * Regression coverage for the photo-viewer "off by one" bug: the gallery grid
 * (paged query) and the viewer's flat list ([JobDao.getVisibleJobs]) must agree
 * on order, and that order must be deterministic across re-queries. Both were
 * `ORDER BY createdAt DESC` with no tiebreaker, so jobs sharing a timestamp
 * (e.g. a batch submit) could be returned in different orders by the two
 * queries — and reshuffle on re-emission — moving a different job under the
 * pager's pinned index.
 */
@RunWith(RobolectricTestRunner::class)
class JobDaoOrderingTest {

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

    private fun job(id: String, createdAt: Long) = JobEntity(
        id = id,
        status = "done",
        inputId = null,
        promptId = null,
        promptText = null,
        workflow = null,
        seed = null,
        progress = 1f,
        error = null,
        createdAt = createdAt,
        startedAt = null,
        completedAt = createdAt,
        durationSeconds = 0,
        width = null,
        height = null,
    )

    private suspend fun pagedAllIds(): List<String> {
        val page = dao.pagedDoneJobsAll().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        return page.data.map { it.id }
    }

    @Test
    fun `grid and viewer queries agree on order for same-timestamp jobs`() = runBlocking {
        // All share one createdAt, inserted out of id order, to expose any
        // reliance on insertion/rowid order.
        listOf("c", "a", "e", "b", "d").forEach { dao.insertJob(job(it, createdAt = 1000)) }

        val viewerIds = dao.getVisibleJobs().first().map { it.id }
        val gridIds = pagedAllIds()

        // Deterministic: createdAt DESC, then id DESC as the stable tiebreaker.
        val expected = listOf("e", "d", "c", "b", "a")
        assertEquals(expected, viewerIds)
        assertEquals(expected, gridIds)
        assertEquals(viewerIds, gridIds)
    }

    @Test
    fun `order is stable across mixed timestamps with ties`() = runBlocking {
        dao.insertJob(job("a", createdAt = 200))
        dao.insertJob(job("b", createdAt = 100))
        dao.insertJob(job("c", createdAt = 200)) // tie with a
        dao.insertJob(job("d", createdAt = 100)) // tie with b

        val expected = listOf("c", "a", "d", "b") // 200s first (id DESC), then 100s (id DESC)
        assertEquals(expected, dao.getVisibleJobs().first().map { it.id })
        assertEquals(expected, pagedAllIds())
    }
}
