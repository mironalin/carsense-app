package com.carsense.features.dtc.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API Service interface for Diagnostic Trouble Code (DTC) related endpoints.
 */
interface DTCApiService {

    /**
     * Create multiple DTCs for a diagnostic record in a single request.
     *
     * @param diagnosticUUID UUID of the diagnostic record
     * @param dtcs List of DTC objects to create
     * @return Response containing the created DTCs
     */
    @POST("diagnostics/{diagnosticUUID}/dtcs")
    suspend fun createDTCs(
        @Path("diagnosticUUID") diagnosticUUID: String,
        @Body dtcs: List<CreateDTCRequest>
    ): Response<CreateDTCsResponse>
} 