package com.carsense.features.location.data.repository

import android.location.Location
import android.util.Log
import com.carsense.features.location.data.service.LocationService
import com.carsense.features.location.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationService: LocationService
) : LocationRepository {

    private val TAG = "LocationRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun getCurrentLocation(): Location? {
        return try {
            locationService.getLastKnownLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location: ${e.message}", e)
            null
        }
    }

    override fun startLocationUpdates(intervalMillis: Long): Boolean {
        return try {
            locationService.requestLocationUpdates(intervalMillis)
                .onEach { location ->
                    Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
                }
                .catch { e ->
                    Log.e(TAG, "Error in location updates: ${e.message}", e)
                }
                .launchIn(scope)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates: ${e.message}", e)
            false
        }
    }

    override fun stopLocationUpdates() {
        try {
            locationService.stopLocationUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates: ${e.message}", e)
        }
    }
} 