package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    // Run history
    @Query("SELECT * FROM run_history ORDER BY score DESC LIMIT 10")
    fun getTopRuns(): Flow<List<RunHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunHistory)

    // Player profile
    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun getPlayerProfile(): Flow<PlayerProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: PlayerProfile)
}
