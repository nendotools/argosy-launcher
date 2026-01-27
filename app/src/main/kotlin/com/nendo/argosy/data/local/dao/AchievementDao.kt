package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.AchievementEntity

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements WHERE gameId = :gameId ORDER BY points DESC, title ASC")
    suspend fun getByGameId(gameId: Long): List<AchievementEntity>

    @Query("SELECT COUNT(*) FROM achievements WHERE gameId = :gameId")
    suspend fun countByGameId(gameId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<AchievementEntity>)

    @Query("DELETE FROM achievements WHERE gameId = :gameId")
    suspend fun deleteByGameId(gameId: Long)

    @Transaction
    suspend fun replaceForGame(gameId: Long, achievements: List<AchievementEntity>) {
        deleteByGameId(gameId)
        insertAll(achievements)
    }

    @Query("SELECT * FROM achievements WHERE badgeUrl IS NOT NULL AND cachedBadgeUrl IS NULL")
    suspend fun getWithUncachedBadges(): List<AchievementEntity>

    @Query("UPDATE achievements SET cachedBadgeUrl = :cachedPath WHERE id = :id")
    suspend fun updateCachedBadgeUrl(id: Long, cachedPath: String)

    @Query("UPDATE achievements SET cachedBadgeUrlLock = :cachedPath WHERE id = :id")
    suspend fun updateCachedBadgeUrlLock(id: Long, cachedPath: String)

    @Query("SELECT COUNT(*) FROM achievements WHERE badgeUrl IS NOT NULL")
    suspend fun countWithBadges(): Int

    @Query("SELECT COUNT(*) FROM achievements WHERE cachedBadgeUrl IS NOT NULL")
    suspend fun countWithCachedBadges(): Int

    @Query("UPDATE achievements SET unlockedAt = :unlockedAt WHERE gameId = :gameId AND raId = :raId")
    suspend fun markUnlocked(gameId: Long, raId: Long, unlockedAt: Long)

    @Query("UPDATE achievements SET unlockedHardcoreAt = :unlockedAt WHERE gameId = :gameId AND raId = :raId")
    suspend fun markUnlockedHardcore(gameId: Long, raId: Long, unlockedAt: Long)

    @Query("SELECT COUNT(*) FROM achievements WHERE gameId = :gameId AND (unlockedAt IS NOT NULL OR unlockedHardcoreAt IS NOT NULL)")
    suspend fun countUnlockedByGameId(gameId: Long): Int
}
