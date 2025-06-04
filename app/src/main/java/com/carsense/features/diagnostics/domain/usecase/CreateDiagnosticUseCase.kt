package com.carsense.features.diagnostics.domain.usecase

import android.util.Log
import com.carsense.features.diagnostics.domain.model.Diagnostic
import com.carsense.features.diagnostics.domain.repository.DiagnosticRepository
import com.carsense.features.location.domain.repository.LocationRepository
import javax.inject.Inject

/**
 * Use case for creating a new diagnostic record with current location (if available)
 */
class CreateDiagnosticUseCase @Inject constructor(
    private val diagnosticRepository: DiagnosticRepository,
    private val locationRepository: LocationRepository
) {
    private val TAG = "CreateDiagnosticUseCase"

    /**
     * Creates a new diagnostic record
     * @param vehicleUuid The UUID of the vehicle being diagnosed
     * @param odometer The current odometer reading
     * @param includeLocation Whether to include the current location (default: true)
     * @param notes Optional notes about the diagnostic session
     * @return Result containing the created Diagnostic on success, or an exception on failure
     */
    suspend operator fun invoke(
        vehicleUuid: String,
        odometer: Int,
        includeLocation: Boolean = true,
        notes: String? = null
    ): Result<Diagnostic> {
        Log.d(
            TAG,
            "Creating diagnostic: vehicleUuid=$vehicleUuid, odometer=$odometer, includeLocation=$includeLocation"
        )

        var locationLat: Double? = null
        var locationLong: Double? = null

        // Try to get the current location if requested
        if (includeLocation) {
            try {
                Log.d(TAG, "Attempting to get current location from repository")
                val location = locationRepository.getCurrentLocation()
                locationLat = location?.latitude
                locationLong = location?.longitude

                if (location != null) {
                    Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                } else {
                    Log.d(TAG, "No location available")
                }
            } catch (e: Exception) {
                // Just log the error and continue without location
                Log.e(TAG, "Error getting location: ${e.message}", e)
            }
        }

        Log.d(
            TAG,
            "Calling diagnosticRepository.createDiagnostic with vehicleUuid=$vehicleUuid, odometer=$odometer, location=($locationLat,$locationLong)"
        )
        return try {
            val result = diagnosticRepository.createDiagnostic(
                vehicleUuid = vehicleUuid,
                odometer = odometer,
                locationLat = locationLat,
                locationLong = locationLong,
                notes = notes
            )

            if (result.isSuccess) {
                Log.d(TAG, "Successfully created diagnostic with UUID: ${result.getOrNull()?.uuid}")
            } else {
                Log.e(TAG, "Failed to create diagnostic: ${result.exceptionOrNull()?.message}")
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating diagnostic: ${e.message}", e)
            Result.failure(e)
        }
    }
} 