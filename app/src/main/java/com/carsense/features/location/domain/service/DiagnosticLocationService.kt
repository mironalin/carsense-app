package com.carsense.features.location.domain.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.carsense.features.location.data.service.ForegroundLocationService
import com.carsense.features.location.data.sync.LocationSyncManager
import com.carsense.features.location.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that manages location tracking during diagnostic sessions.
 * Handles starting/stopping location tracking, bulk uploads, and coordination with sync manager.
 */
@Singleton
class DiagnosticLocationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationSyncManager: LocationSyncManager,
    private val locationRepository: LocationRepository
) {
    private val TAG = "DiagnosticLocationService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentDiagnosticUUID: String? = null
    private var isLocationTrackingActive = false

    /**
     * Starts location tracking for a diagnostic session
     */
    fun startLocationTrackingForDiagnostic(
        diagnosticUUID: String,
        vehicleUUID: String
    ) {
        Log.d(
            TAG,
            "Starting location tracking for diagnostic: $diagnosticUUID, vehicle: $vehicleUUID"
        )

        try {
            // Stop any existing tracking first
            if (isLocationTrackingActive) {
                stopLocationTracking()
            }

            currentDiagnosticUUID = diagnosticUUID

            // Start the sync manager for this diagnostic
            locationSyncManager.startSyncForDiagnostic(diagnosticUUID)

            // Start the foreground location service
            val intent = Intent(context, ForegroundLocationService::class.java).apply {
                action = ForegroundLocationService.ACTION_START_LOCATION_SERVICE
                putExtra(ForegroundLocationService.EXTRA_VEHICLE_UUID, vehicleUUID)
                putExtra(ForegroundLocationService.EXTRA_DIAGNOSTIC_UUID, diagnosticUUID)
            }

            context.startForegroundService(intent)
            isLocationTrackingActive = true

            Log.d(TAG, "Location tracking started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking: ${e.message}", e)
        }
    }

    /**
     * Stops location tracking and uploads all remaining locations
     */
    suspend fun stopLocationTrackingAndUpload(): Boolean {
        Log.d(TAG, "Stopping location tracking and uploading remaining locations")

        return try {
            // Stop the foreground location service
            val intent = Intent(context, ForegroundLocationService::class.java).apply {
                action = ForegroundLocationService.ACTION_STOP_LOCATION_SERVICE
            }
            context.startService(intent)

            // Stop sync manager and upload remaining locations
            val uploadSuccess = locationSyncManager.stopSyncAndUploadRemaining()

            isLocationTrackingActive = false
            currentDiagnosticUUID = null

            Log.d(TAG, "Location tracking stopped, upload success: $uploadSuccess")
            uploadSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location tracking: ${e.message}", e)
            false
        }
    }

    /**
     * Stops location tracking without uploading (for emergency stops)
     */
    fun stopLocationTracking() {
        Log.d(TAG, "Emergency stop of location tracking")

        try {
            val intent = Intent(context, ForegroundLocationService::class.java).apply {
                action = ForegroundLocationService.ACTION_STOP_LOCATION_SERVICE
            }
            context.startService(intent)

            isLocationTrackingActive = false
            currentDiagnosticUUID = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during emergency stop: ${e.message}", e)
        }
    }

    /**
     * Triggers a bulk upload check (can be called periodically)
     */
    fun triggerBulkUploadCheck() {
        if (isLocationTrackingActive) {
            locationSyncManager.triggerAsyncBulkUploadCheck()
        }
    }

    /**
     * Gets the count of unsynced locations for the current diagnostic session
     */
    suspend fun getUnsyncedLocationCount(): Int {
        val diagnosticUUID = currentDiagnosticUUID
        return if (diagnosticUUID != null) {
            try {
                locationRepository.getUnsyncedLocationPoints(diagnosticUUID).size
            } catch (e: Exception) {
                Log.e(TAG, "Error getting unsynced location count: ${e.message}", e)
                0
            }
        } else {
            0
        }
    }

    /**
     * Manually uploads all unsynced locations for the current diagnostic session
     */
    suspend fun manualUploadLocations(): Result<Int> {
        val diagnosticUUID = currentDiagnosticUUID
        return if (diagnosticUUID != null) {
            try {
                val unsyncedLocations = locationRepository.getUnsyncedLocationPoints(diagnosticUUID)
                if (unsyncedLocations.isNotEmpty()) {
                    val result =
                        locationRepository.uploadLocationsBulk(diagnosticUUID, unsyncedLocations)
                    result.onSuccess { count ->
                        // Mark as synced
                        locationRepository.markLocationPointsAsSyncedByUuid(unsyncedLocations.map { it.uuid })
                        Log.d(TAG, "Manually uploaded $count locations")
                    }
                    result
                } else {
                    Result.success(0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during manual upload: ${e.message}", e)
                Result.failure(e)
            }
        } else {
            Result.failure(IllegalStateException("No active diagnostic session"))
        }
    }

    /**
     * Checks if location tracking is currently active
     */
    fun isTrackingActive(): Boolean = isLocationTrackingActive

    /**
     * Gets the current diagnostic UUID if tracking is active
     */
    fun getCurrentDiagnosticUUID(): String? = currentDiagnosticUUID
} 