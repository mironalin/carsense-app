package com.carsense.features.location.domain.repository

import android.location.Location

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
} 