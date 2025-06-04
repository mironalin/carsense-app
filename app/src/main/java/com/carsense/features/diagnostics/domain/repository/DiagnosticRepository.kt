package com.carsense.features.diagnostics.domain.repository

import com.carsense.features.diagnostics.domain.model.Diagnostic

/**
 * Repository interface for diagnostic operations
 */
interface DiagnosticRepository {
    /**
     * Creates a new diagnostic record
     * @param vehicleUuid The UUID of the vehicle being diagnosed
     * @param odometer The current odometer reading
     * @param locationLat Optional latitude coordinate
     * @param locationLong Optional longitude coordinate
     * @param notes Optional notes about the diagnostic session
     * @return Result containing the created Diagnostic on success, or an exception on failure
     */
    suspend fun createDiagnostic(
        vehicleUuid: String,
        odometer: Int,
        locationLat: Double? = null,
        locationLong: Double? = null,
        notes: String? = null
    ): Result<Diagnostic>

    /**
     * Gets a diagnostic by its UUID
     * @param uuid The UUID of the diagnostic record
     * @return Result containing the Diagnostic on success, or an exception on failure
     */
    suspend fun getDiagnosticByUuid(uuid: String): Result<Diagnostic>

    /**
     * Gets all diagnostics for the user's vehicles
     * @return Result containing a list of Diagnostics on success, or an exception on failure
     */
    suspend fun getAllDiagnostics(): Result<List<Diagnostic>>
} 