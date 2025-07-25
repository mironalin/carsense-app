package com.carsense.features.dtc.domain.repository

import com.carsense.features.dtc.domain.model.DTCError

/** Repository interface for interacting with vehicle Diagnostic Trouble Codes (DTCs) */
interface DTCRepository {
    /**
     * Get the list of current DTCs from the vehicle
     * @return Flow emitting a list of DTCError objects
     */
    suspend fun getDTCs(): Result<List<DTCError>>

    /**
     * Clear all DTCs from the vehicle and reset the Check Engine Light
     * @return true if clearing was successful, false otherwise
     */
    suspend fun clearDTCs(): Result<Boolean>

    /**
     * Get the most recently cached DTCs without performing a new scan
     * @return List of cached DTCError objects, or empty list if no cache exists
     */
    fun getCachedDTCs(): List<DTCError>

    /**
     * Send the current cached DTCs to the backend for the specified diagnostic
     * @param diagnosticUUID The UUID of the diagnostic record to associate with the DTCs
     * @return Result with the count of DTCs sent, or an error
     */
    suspend fun sendDTCsToBackend(diagnosticUUID: String): Result<Int>
}
