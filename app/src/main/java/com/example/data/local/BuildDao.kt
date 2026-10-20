package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildDao {

    @Query("SELECT * FROM build_records ORDER BY createdAt DESC")
    fun getAllBuilds(): Flow<List<BuildEntity>>

    @Query("SELECT * FROM build_records WHERE status IN ('QUEUED', 'IN_PROGRESS') ORDER BY createdAt DESC")
    fun getActiveBuilds(): Flow<List<BuildEntity>>

    @Query("SELECT COUNT(*) FROM build_records WHERE status = 'SUCCESS'")
    fun getSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM build_records WHERE status = 'FAILURE'")
    fun getFailureCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM download_records")
    fun getTotalDownloadsCount(): Flow<Int>

    @Query("SELECT * FROM build_records WHERE id = :id")
    suspend fun getBuildById(id: Long): BuildEntity?

    @Query("SELECT * FROM build_records WHERE workflowRunId = :runId LIMIT 1")
    suspend fun getBuildByRunId(runId: Long): BuildEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(build: BuildEntity): Long

    @Update
    suspend fun updateBuild(build: BuildEntity)

    @Delete
    suspend fun deleteBuild(build: BuildEntity)

    @Query("DELETE FROM build_records")
    suspend fun clearAllBuilds()

    // Download records
    @Query("SELECT * FROM download_records ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadRecordEntity): Long

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM download_records")
    suspend fun clearAllDownloads()
}
