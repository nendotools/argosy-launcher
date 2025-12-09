package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmulatorSaveConfigDao {

    @Query("SELECT * FROM emulator_save_config WHERE emulatorId = :emulatorId")
    suspend fun getByEmulator(emulatorId: String): EmulatorSaveConfigEntity?

    @Query("SELECT * FROM emulator_save_config")
    suspend fun getAll(): List<EmulatorSaveConfigEntity>

    @Query("SELECT * FROM emulator_save_config")
    fun observeAll(): Flow<List<EmulatorSaveConfigEntity>>

    @Query("SELECT * FROM emulator_save_config WHERE isUserOverride = 1")
    suspend fun getUserOverrides(): List<EmulatorSaveConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmulatorSaveConfigEntity)

    @Query("DELETE FROM emulator_save_config WHERE emulatorId = :emulatorId")
    suspend fun delete(emulatorId: String)

    @Query("DELETE FROM emulator_save_config WHERE isAutoDetected = 1")
    suspend fun clearAutoDetected()
}
