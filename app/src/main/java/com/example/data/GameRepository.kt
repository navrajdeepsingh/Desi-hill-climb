package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameDao: GameDao) {
    val topRuns: Flow<List<RunHistory>> = gameDao.getTopRuns()
    val playerProfile: Flow<PlayerProfile?> = gameDao.getPlayerProfile()

    suspend fun saveRun(run: RunHistory) {
        gameDao.insertRun(run)
    }

    suspend fun saveProfile(profile: PlayerProfile) {
        gameDao.insertOrUpdateProfile(profile)
    }
}
