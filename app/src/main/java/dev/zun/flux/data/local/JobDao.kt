package dev.zun.flux.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PromptIdCount(val promptId: Long, val jobCount: Int)

data class JobTagTotals(val customCount: Int, val totalCount: Int)

/**
 * A stack's cover row (its most-recently-created member) plus how many members the stack has,
 * where a "stack" is every done job sharing [JobEntity.lineageRootId] (falling back to its own
 * id when unset). [stackCount] respects whatever prompt/tag + favorites-only predicate the
 * enclosing query applies — it does not account for the client-side free-text search filter,
 * which is applied after paging (see research.md Decision 1's scoping note). [stackHasFavorite]
 * is true when *any* member of the stack (cover or not) is favorited — lets the gallery show a
 * "something in here is favorited" cue on a stack whose own cover isn't the favorited one,
 * without implying the whole stack was favorited.
 */
data class JobWithStackCount(@Embedded val job: JobEntity, val stackCount: Int, val stackHasFavorite: Boolean)

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<JobEntity>>

    @Query(
        """
        SELECT * FROM jobs
        WHERE id NOT IN (SELECT jobId FROM pending_deletes)
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun getVisibleJobs(): Flow<List<JobEntity>>

    @Query(
        """
        SELECT jobs.*,
            (SELECT COUNT(*) FROM jobs j2
             WHERE j2.status = 'done'
             AND j2.id NOT IN (SELECT jobId FROM pending_deletes)
             AND (:favoritesOnly = 0 OR j2.isFavorite = 1)
             AND (j2.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j2.lineageRootId IS NULL AND j2.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ) AS stackCount,
            (SELECT MAX(j2.isFavorite) FROM jobs j2
             WHERE j2.status = 'done'
             AND j2.id NOT IN (SELECT jobId FROM pending_deletes)
             AND (:favoritesOnly = 0 OR j2.isFavorite = 1)
             AND (j2.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j2.lineageRootId IS NULL AND j2.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ) AS stackHasFavorite
        FROM jobs
        WHERE status = 'done'
        AND id NOT IN (SELECT jobId FROM pending_deletes)
        AND (:favoritesOnly = 0 OR isFavorite = 1)
        AND jobs.id = (
            SELECT j3.id FROM jobs j3
            WHERE j3.status = 'done'
            AND j3.id NOT IN (SELECT jobId FROM pending_deletes)
            AND (:favoritesOnly = 0 OR j3.isFavorite = 1)
            AND (j3.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j3.lineageRootId IS NULL AND j3.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ORDER BY j3.createdAt DESC, j3.id DESC
            LIMIT 1
        )
        ORDER BY
            CASE WHEN :newestFirst THEN createdAt END DESC,
            CASE WHEN :newestFirst THEN id END DESC,
            CASE WHEN NOT :newestFirst THEN createdAt END ASC,
            CASE WHEN NOT :newestFirst THEN id END ASC
        """,
    )
    fun pagedDoneJobsAll(newestFirst: Boolean, favoritesOnly: Boolean): PagingSource<Int, JobWithStackCount>

    @Query(
        """
        SELECT jobs.*,
            (SELECT COUNT(*) FROM jobs j2
             WHERE j2.status = 'done'
             AND j2.promptId = :promptId
             AND j2.id NOT IN (SELECT jobId FROM pending_deletes)
             AND (:favoritesOnly = 0 OR j2.isFavorite = 1)
             AND (j2.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j2.lineageRootId IS NULL AND j2.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ) AS stackCount,
            (SELECT MAX(j2.isFavorite) FROM jobs j2
             WHERE j2.status = 'done'
             AND j2.promptId = :promptId
             AND j2.id NOT IN (SELECT jobId FROM pending_deletes)
             AND (:favoritesOnly = 0 OR j2.isFavorite = 1)
             AND (j2.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j2.lineageRootId IS NULL AND j2.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ) AS stackHasFavorite
        FROM jobs
        WHERE status = 'done'
        AND promptId = :promptId
        AND id NOT IN (SELECT jobId FROM pending_deletes)
        AND (:favoritesOnly = 0 OR isFavorite = 1)
        AND jobs.id = (
            SELECT j3.id FROM jobs j3
            WHERE j3.status = 'done'
            AND j3.promptId = :promptId
            AND j3.id NOT IN (SELECT jobId FROM pending_deletes)
            AND (:favoritesOnly = 0 OR j3.isFavorite = 1)
            AND (j3.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j3.lineageRootId IS NULL AND j3.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ORDER BY j3.createdAt DESC, j3.id DESC
            LIMIT 1
        )
        ORDER BY
            CASE WHEN :newestFirst THEN createdAt END DESC,
            CASE WHEN :newestFirst THEN id END DESC,
            CASE WHEN NOT :newestFirst THEN createdAt END ASC,
            CASE WHEN NOT :newestFirst THEN id END ASC
        """,
    )
    fun pagedDoneJobsByPromptId(promptId: Long, newestFirst: Boolean, favoritesOnly: Boolean): PagingSource<Int, JobWithStackCount>

    @Query(
        """
        SELECT jobs.*,
            (SELECT COUNT(*) FROM jobs j2
             WHERE j2.status = 'done'
             AND j2.promptId IS NULL
             AND j2.promptText IS NOT NULL
             AND j2.id NOT IN (SELECT jobId FROM pending_deletes)
             AND (:favoritesOnly = 0 OR j2.isFavorite = 1)
             AND (j2.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j2.lineageRootId IS NULL AND j2.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ) AS stackCount,
            (SELECT MAX(j2.isFavorite) FROM jobs j2
             WHERE j2.status = 'done'
             AND j2.promptId IS NULL
             AND j2.promptText IS NOT NULL
             AND j2.id NOT IN (SELECT jobId FROM pending_deletes)
             AND (:favoritesOnly = 0 OR j2.isFavorite = 1)
             AND (j2.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j2.lineageRootId IS NULL AND j2.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ) AS stackHasFavorite
        FROM jobs
        WHERE status = 'done'
        AND promptId IS NULL
        AND promptText IS NOT NULL
        AND id NOT IN (SELECT jobId FROM pending_deletes)
        AND (:favoritesOnly = 0 OR isFavorite = 1)
        AND jobs.id = (
            SELECT j3.id FROM jobs j3
            WHERE j3.status = 'done'
            AND j3.promptId IS NULL
            AND j3.promptText IS NOT NULL
            AND j3.id NOT IN (SELECT jobId FROM pending_deletes)
            AND (:favoritesOnly = 0 OR j3.isFavorite = 1)
            AND (j3.lineageRootId = COALESCE(jobs.lineageRootId, jobs.id) OR (j3.lineageRootId IS NULL AND j3.id = COALESCE(jobs.lineageRootId, jobs.id)))
            ORDER BY j3.createdAt DESC, j3.id DESC
            LIMIT 1
        )
        ORDER BY
            CASE WHEN :newestFirst THEN createdAt END DESC,
            CASE WHEN :newestFirst THEN id END DESC,
            CASE WHEN NOT :newestFirst THEN createdAt END ASC,
            CASE WHEN NOT :newestFirst THEN id END ASC
        """,
    )
    fun pagedDoneJobsCustom(newestFirst: Boolean, favoritesOnly: Boolean): PagingSource<Int, JobWithStackCount>

    @Query(
        """
        SELECT promptId AS promptId, COUNT(*) AS jobCount
        FROM jobs
        WHERE status = 'done'
        AND promptId IS NOT NULL
        AND id NOT IN (SELECT jobId FROM pending_deletes)
        GROUP BY promptId
        """,
    )
    fun jobCountsByPromptId(): Flow<List<PromptIdCount>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN promptId IS NULL AND promptText IS NOT NULL THEN 1 ELSE 0 END), 0) AS customCount,
            COUNT(*) AS totalCount
        FROM jobs
        WHERE status = 'done'
        AND id NOT IN (SELECT jobId FROM pending_deletes)
        """,
    )
    fun jobTagTotals(): Flow<JobTagTotals>

    @Query(
        """
        SELECT * FROM jobs
        WHERE status NOT IN ('done', 'failed', 'cancelled')
        AND id NOT IN (SELECT jobId FROM pending_deletes)
        ORDER BY createdAt ASC, id ASC
        """,
    )
    fun getActiveJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: String): JobEntity?

    @Query(
        """
        SELECT * FROM jobs
        WHERE id = :jobId
        AND id NOT IN (SELECT jobId FROM pending_deletes)
        """,
    )
    fun getVisibleJobByIdFlow(jobId: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :jobId")
    fun getJobByIdFlow(jobId: String): Flow<JobEntity?>

    @Query(
        """
        SELECT * FROM jobs
        WHERE status = 'done'
        AND (sourceSha256 = :hash OR resultSha256 = :hash)
        ORDER BY createdAt ASC
        LIMIT 1
        """,
    )
    suspend fun findDoneJobByHash(hash: String): JobEntity?

    @Query(
        """
        SELECT * FROM jobs
        WHERE status = 'done'
        AND inputId = :inputId
        AND sourceSha256 IS NOT NULL
        ORDER BY createdAt ASC
        LIMIT 1
        """,
    )
    suspend fun findDoneJobByInputId(inputId: Int): JobEntity?

    @Query("UPDATE jobs SET resultSha256 = :hash WHERE id = :jobId")
    suspend fun updateResultHash(jobId: String, hash: String)

    @Query("UPDATE jobs SET sourceSha256 = :hash, lineageRootId = :rootId WHERE id = :jobId")
    suspend fun updateSourceLineage(jobId: String, hash: String, rootId: String)

    @Query("UPDATE jobs SET isFavorite = :isFavorite WHERE id = :jobId")
    suspend fun setFavorite(jobId: String, isFavorite: Boolean)

    @Query(
        """
        SELECT * FROM jobs
        WHERE lineageRootId = :rootId
        AND status = 'done'
        ORDER BY createdAt ASC
        """,
    )
    fun getJobsByLineageRoot(rootId: String): Flow<List<JobEntity>>

    @Query("SELECT COUNT(*) FROM jobs WHERE lineageRootId = :rootId AND status = 'done'")
    suspend fun countByLineageRoot(rootId: String): Int

    @Query("SELECT * FROM jobs WHERE id IN (:ids)")
    suspend fun getJobsByIds(ids: List<String>): List<JobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<JobEntity>)

    @Query("DELETE FROM jobs WHERE id = :jobId")
    suspend fun deleteJobById(jobId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDelete(pendingDelete: PendingDeleteEntity)

    @Query("DELETE FROM pending_deletes WHERE jobId = :jobId")
    suspend fun deletePendingDelete(jobId: String)

    @Query("SELECT * FROM pending_deletes ORDER BY createdAt ASC")
    suspend fun getPendingDeletes(): List<PendingDeleteEntity>

    @Query("SELECT jobId FROM pending_deletes")
    suspend fun getPendingDeleteIds(): List<String>

    @Query("SELECT jobId FROM pending_deletes")
    fun getPendingDeleteIdsFlow(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_deletes WHERE jobId = :jobId)")
    suspend fun isPendingDelete(jobId: String): Boolean
}
