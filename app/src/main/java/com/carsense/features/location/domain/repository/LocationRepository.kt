package com.carsense.features.location.domain.repository

import android.location.Location
import com.carsense.features.location.domain.model.LocationPoint

/**
 * Repository interface for accessing location data
 */
interface LocationRepository {
    /**
     * Gets the current device location
     * @return The current Location or null if unavailable
     */
    suspend fun getCurrentLocation(): Location?

    /**
     * Starts location updates
     * @param intervalMillis The interval between location updates in milliseconds
     * @return Whether location updates were successfully started
     */
    fun startLocationUpdates(intervalMillis: Long = 2000): Boolean

    /**
     * Stops location updates
     */
    fun stopLocationUpdates()

    /**
     * Saves a location point to local storage
     * @param locationPoint The location point to save
     */
    suspend fun saveLocationPoint(locationPoint: LocationPoint)

    /**
     * Gets unsynced location points for a specific diagnostic session
     * @param diagnosticUUID The UUID of the diagnostic session
     * @return List of unsynced location points
     */
    suspend fun getUnsyncedLocationPoints(diagnosticUUID: String): List<LocationPoint>

    /**
     * Uploads location points to the server in bulk
     * @param diagnosticUUID The UUID of the diagnostic session
     * @param locationPoints The location points to upload
     * @return Result indicating success or failure
     */
    suspend fun uploadLocationsBulk(
        diagnosticUUID: String,
        locationPoints: List<LocationPoint>
    ): Result<Int> // Returns count of uploaded locations

    /**
     * Marks location points as synced
     * @param localIds The local IDs of the location points to mark as synced
     */
    suspend fun markLocationPointsAsSynced(localIds: List<Long>)

    /**
     * Marks location points as synced by UUID
     * @param uuids The UUIDs of the location points to mark as synced
     */
    suspend fun markLocationPointsAsSyncedByUuid(uuids: List<String>)
} 