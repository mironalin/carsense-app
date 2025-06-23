package com.carsense.features.location.data.repository

import android.location.Location
import android.util.Log
import com.carsense.core.room.dao.LocationPointDao
import com.carsense.features.location.data.api.LocationApiService
import com.carsense.features.location.data.mapper.LocationMapper
import com.carsense.features.location.data.service.LocationService
import com.carsense.features.location.domain.model.LocationPoint
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
    private val locationService: LocationService,
    private val locationPointDao: LocationPointDao,
    private val locationApiService: LocationApiService
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

    override suspend fun saveLocationPoint(locationPoint: LocationPoint) {
        try {
            val entity = LocationMapper.toEntity(locationPoint)
            locationPointDao.insert(entity)
            Log.d(TAG, "Location point saved: ${locationPoint.uuid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location point: ${e.message}", e)
        }
    }

    override suspend fun getUnsyncedLocationPoints(diagnosticUUID: String): List<LocationPoint> {
        return try {
            val entities = locationPointDao.getUnsyncedLocationsByDiagnosticUuid(diagnosticUUID)
            LocationMapper.toDomainModelList(entities)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unsynced locations: ${e.message}", e)
            emptyList()
        }
    }

    override suspend fun uploadLocationsBulk(
        diagnosticUUID: String,
        locationPoints: List<LocationPoint>
    ): Result<Int> {
        return try {
            if (locationPoints.isEmpty()) {
                return Result.success(0)
            }

            val apiRequests = LocationMapper.toApiRequestList(locationPoints)
            val response = locationApiService.createBulkLocations(diagnosticUUID, apiRequests)

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    Log.d(
                        TAG,
                        "Successfully uploaded ${responseBody.count} locations for diagnostic $diagnosticUUID"
                    )
                    Result.success(responseBody.count)
                } else {
                    Log.e(TAG, "Empty response body for location upload")
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Log.e(TAG, "Failed to upload locations: ${response.code()} - ${response.message()}")
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during location upload: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun markLocationPointsAsSynced(localIds: List<Long>) {
        try {
            if (localIds.isNotEmpty()) {
                val markedCount = locationPointDao.markAsSynced(localIds)
                Log.d(TAG, "Marked $markedCount location points as synced")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking location points as synced: ${e.message}", e)
        }
    }

    override suspend fun markLocationPointsAsSyncedByUuid(uuids: List<String>) {
        try {
            if (uuids.isNotEmpty()) {
                val markedCount = locationPointDao.markAsSyncedByUuid(uuids)
                Log.d(TAG, "Marked $markedCount location points as synced by UUID")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking location points as synced by UUID: ${e.message}", e)
        }
    }
} 