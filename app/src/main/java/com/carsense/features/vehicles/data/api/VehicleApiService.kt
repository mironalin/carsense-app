package com.carsense.features.vehicles.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API Service interface for Vehicle related endpoints.
 */
interface VehicleApiService {

    /**
     * Get all vehicles owned by the user.
     * The Authorization header with the bearer token will be added by the AuthInterceptor.
     */
    @GET("vehicles")
    suspend fun getAllVehicles(): Response<List<VehicleDto>>

    /**
     * Get a specific vehicle by UUID.
     */
    @GET("vehicles/{vehicleUUID}")
    suspend fun getVehicleByUuid(@Path("vehicleUUID") uuid: String): Response<VehicleDto>

    /**
     * Create a new vehicle.
     */
    @POST("vehicles")
    suspend fun createVehicle(@Body request: CreateVehicleRequest): Response<CreateVehicleResponse>

    /**
     * Update a vehicle.
     */
    @PATCH("vehicles/{vehicleUUID}")
    suspend fun updateVehicle(
        @Path("vehicleUUID") uuid: String, @Body request: UpdateVehicleRequest
    ): Response<VehicleDto>

    /**
     * Delete a vehicle.
     */
    @DELETE("vehicles/{vehicleUUID}")
    suspend fun deleteVehicle(@Path("vehicleUUID") uuid: String): Response<DeleteVehicleResponse>

    /**
     * Restore a deleted vehicle.
     */
    @POST("vehicles/{vehicleUUID}/restore")
    suspend fun restoreVehicle(@Path("vehicleUUID") uuid: String): Response<RestoreVehicleResponse>
} 