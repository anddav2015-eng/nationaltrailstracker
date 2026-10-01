package com.anddav.nationaltrailstracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StageLogDao {
    @Query("SELECT * FROM stage_logs")
    fun observeAll(): Flow<List<StageLogEntity>>

    @Query("SELECT * FROM stage_logs WHERE trailId = :trailId")
    fun observeForTrail(trailId: String): Flow<List<StageLogEntity>>

    @Query("SELECT COUNT(*) FROM stage_logs")
    suspend fun count(): Int

    @Query("SELECT * FROM stage_logs")
    suspend fun getAllOnce(): List<StageLogEntity>

    @Query("DELETE FROM stage_logs")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: StageLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<StageLogEntity>)

    @Update
    suspend fun update(log: StageLogEntity)

    @Delete
    suspend fun delete(log: StageLogEntity)
}