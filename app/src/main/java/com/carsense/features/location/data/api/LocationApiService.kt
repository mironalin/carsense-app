package com.carsense.features.location.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API Service interface for Location related endpoints.
 * Handles bulk location uploads tied to diagnostic sessions.
 */
interface LocationApiService {

    /**
     * Create multiple locations for a diagnostic record in a single request.
     * This is optimized for mobile apps that collect location data frequently
     * during a diagnostic session.
     *
     * @param diagnosticUUID UUID of the diagnostic record
     * @param locations List of location objects to create
     * @return Response containing the created locations
     */
    @POST("locations/{diagnosticUUID}/bulk")
    suspend fun createBulkLocations(
        @Path("diagnosticUUID") diagnosticUUID: String,
        @Body locations: List<CreateLocationRequest>
    ): Response<CreateBulkLocationsResponse>
} 