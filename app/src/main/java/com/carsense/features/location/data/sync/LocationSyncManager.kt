package com.carsense.features.location.data.sync

import android.util.Log
import com.carsense.features.location.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the synchronization of location points with the backend API.
 * Handles bulk uploads, thresholds, and ensures no data is lost when sessions end.
 */
@Singleton
class LocationSyncManager @Inject constructor(
    private val locationRepository: LocationRepository
) {
    private val TAG = "LocationSyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Configuration
    private val BULK_UPLOAD_THRESHOLD = 50 // Upload when we have 50+ locations
    private val MAX_RETRY_ATTEMPTS = 3
    private val RETRY_DELAY_MS = 5000L // 5 seconds between retries

    // Current session state
    private var currentDiagnosticUUID: String? = null
    private var isSyncEnabled = false

    /**
     * Starts location sync for a diagnostic session
     */
    fun startSyncForDiagnostic(diagnosticUUID: String) {
        Log.d(TAG, "Starting location sync for diagnostic: $diagnosticUUID")
        currentDiagnosticUUID = diagnosticUUID
        isSyncEnabled = true
    }

    /**
     * Stops location sync and uploads all remaining locations for the current session
     */
    suspend fun stopSyncAndUploadRemaining(): Boolean {
        val diagnosticUUID = currentDiagnosticUUID
        if (diagnosticUUID == null) {
            Log.w(TAG, "No active diagnostic session to stop")
            return true
        }

        Log.d(
            TAG,
            "Stopping sync and uploading remaining locations for diagnostic: $diagnosticUUID"
        )
        isSyncEnabled = false

        // Upload ALL remaining unsynced locations regardless of count
        val success = uploadAllUnsyncedLocations(diagnosticUUID, forceUpload = true)

        currentDiagnosticUUID = null
        return success
    }

    /**
     * Checks if we should trigger a bulk upload based on current unsynced count
     */
    suspend fun checkAndTriggerBulkUpload() {
        val diagnosticUUID = currentDiagnosticUUID
        if (diagnosticUUID == null || !isSyncEnabled) {
            return
        }

        try {
            val unsyncedLocations = locationRepository.getUnsyncedLocationPoints(diagnosticUUID)

            if (unsyncedLocations.size >= BULK_UPLOAD_THRESHOLD) {
                Log.d(TAG, "Threshold reached (${unsyncedLocations.size}), triggering bulk upload")
                uploadAllUnsyncedLocations(diagnosticUUID, forceUpload = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking bulk upload threshold: ${e.message}", e)
        }
    }

    /**
     * Uploads all unsynced locations for a diagnostic session with retry logic
     */
    private suspend fun uploadAllUnsyncedLocations(
        diagnosticUUID: String,
        forceUpload: Boolean
    ): Boolean {
        return try {
            val unsyncedLocations = locationRepository.getUnsyncedLocationPoints(diagnosticUUID)

            if (unsyncedLocations.isEmpty()) {
                Log.d(TAG, "No unsynced locations to upload for diagnostic: $diagnosticUUID")
                return true
            }

            if (!forceUpload && unsyncedLocations.size < BULK_UPLOAD_THRESHOLD) {
                Log.d(
                    TAG,
                    "Not enough locations for bulk upload (${unsyncedLocations.size} < $BULK_UPLOAD_THRESHOLD)"
                )
                return true
            }

            Log.d(
                TAG,
                "Uploading ${unsyncedLocations.size} locations for diagnostic: $diagnosticUUID"
            )

            // Attempt upload with retry logic
            var attempt = 1
            var success = false

            while (attempt <= MAX_RETRY_ATTEMPTS && !success) {
                Log.d(TAG, "Upload attempt $attempt/$MAX_RETRY_ATTEMPTS for $diagnosticUUID")

                val result =
                    locationRepository.uploadLocationsBulk(diagnosticUUID, unsyncedLocations)

                result.fold(
                    onSuccess = { count ->
                        Log.d(TAG, "Successfully uploaded $count locations")

                        // Mark locations as synced
                        markLocationsAsSynced(diagnosticUUID, unsyncedLocations.map { it.uuid })
                        success = true
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Upload attempt $attempt failed: ${exception.message}")

                        if (attempt < MAX_RETRY_ATTEMPTS) {
                            Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                            delay(RETRY_DELAY_MS)
                        } else {
                            Log.e(TAG, "All upload attempts failed for diagnostic: $diagnosticUUID")
                        }
                    }
                )

                attempt++
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception during location upload: ${e.message}", e)
            false
        }
    }

    /**
     * Marks locations as synced by their UUIDs
     */
    private suspend fun markLocationsAsSynced(diagnosticUUID: String, locationUUIDs: List<String>) {
        try {
            locationRepository.markLocationPointsAsSyncedByUuid(locationUUIDs)
            Log.d(
                TAG,
                "Marked ${locationUUIDs.size} locations as synced for diagnostic: $diagnosticUUID"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error marking locations as synced: ${e.message}", e)
        }
    }

    /**
     * Performs an asynchronous bulk upload check (non-blocking)
     */
    fun triggerAsyncBulkUploadCheck() {
        scope.launch {
            checkAndTriggerBulkUpload()
        }
    }
} 