package dev.zun.flux.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<JobEntity>>

    @Query(
        """
        SELECT * FROM jobs
        WHERE id NOT IN (SELECT jobId FROM pending_deletes)
        ORDER BY createdAt DESC
        """,
    )
    fun getVisibleJobs(): Flow<List<JobEntity>>

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
