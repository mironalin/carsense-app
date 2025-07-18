package com.carsense.features.vehicles.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Insert(
        onConflict = OnConflictStrategy.ABORT
    ) // Prevent overwriting if a local vehicle with same PK (localId) somehow exists
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity): Int

    // Mark a vehicle as synced with the server
    @Query(
        "UPDATE vehicles SET is_synced = 1, updated_at_ms = :updatedAt WHERE uuid = :uuid"
    )
    suspend fun markAsSynced(uuid: String, updatedAt: Long): Int

    @Query("SELECT * FROM vehicles WHERE local_id = :localId")
    suspend fun getVehicleByLocalId(localId: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE uuid = :uuid")
    suspend fun getVehicleByUuid(uuid: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE is_synced = 0 ORDER BY created_at_ms ASC")
    fun getUnsyncedVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY make ASC, model ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY make ASC, model ASC")
    suspend fun getAllVehiclesSync(): List<VehicleEntity>

    @Query("DELETE FROM vehicles WHERE local_id = :localId")
    suspend fun deleteByLocalId(localId: Long): Int

    @Query("DELETE FROM vehicles WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String): Int

    @Query(
        "SELECT EXISTS(SELECT 1 FROM vehicles WHERE vehicle_identification_number = :vin LIMIT 1)"
    )
    suspend fun doesVinExist(vin: String): Boolean

    @Query("SELECT * FROM vehicles ORDER BY local_id DESC LIMIT 1")
    suspend fun getLatestVehicle(): VehicleEntity?

    @Query("DELETE FROM vehicles")
    suspend fun clearAll(): Int
}
