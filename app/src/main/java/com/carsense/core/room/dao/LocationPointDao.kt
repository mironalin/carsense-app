package com.carsense.core.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.carsense.core.room.entity.LocationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(locationPoint: LocationPointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locationPoints: List<LocationPointEntity>): List<Long>

    @Query(
        "SELECT * FROM location_points WHERE vehicle_uuid = :vehicleUUID ORDER BY timestamp DESC"
    )
    fun getLocationsForVehicle(vehicleUUID: String): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE is_synced = 0 ORDER BY timestamp ASC")
    fun getUnsyncedLocationPoints(): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE diagnostic_uuid = :diagnosticUUID ORDER BY timestamp ASC")
    fun getLocationsByDiagnosticUuid(diagnosticUUID: String): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE diagnostic_uuid = :diagnosticUUID AND is_synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLocationsByDiagnosticUuid(diagnosticUUID: String): List<LocationPointEntity>

    @Query("SELECT * FROM location_points WHERE local_id = :localId")
    suspend fun getLocationPointById(localId: Long): LocationPointEntity?

    @Query("SELECT * FROM location_points WHERE uuid = :uuid")
    suspend fun getLocationPointByUuid(uuid: String): LocationPointEntity?

    // Update multiple items. Useful after a successful sync.
    @Update
    suspend fun updateLocationPoints(locationPoints: List<LocationPointEntity>)

    @Query("UPDATE location_points SET is_synced = 1 WHERE local_id IN (:localIds)")
    suspend fun markAsSynced(localIds: List<Long>): Int

    @Query("UPDATE location_points SET is_synced = 1 WHERE uuid IN (:uuids)")
    suspend fun markAsSyncedByUuid(uuids: List<String>): Int

    @Query("DELETE FROM location_points WHERE local_id = :localId")
    suspend fun deleteById(localId: Long): Int

    @Delete
    suspend fun deleteLocationPoints(locationPoints: List<LocationPointEntity>): Int

    @Query("DELETE FROM location_points WHERE vehicle_uuid = :vehicleUUID")
    suspend fun deleteLocationsForVehicle(vehicleUUID: String): Int

    @Query("DELETE FROM location_points")
    suspend fun clearAll(): Int

    @Query("SELECT * FROM location_points ORDER BY timestamp DESC")
    fun getAllLocationPoints(): Flow<List<LocationPointEntity>>
}
