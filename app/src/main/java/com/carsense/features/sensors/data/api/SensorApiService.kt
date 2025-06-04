package com.carsense.features.sensors.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API Service for sensor-related endpoints.
 */
interface SensorApiService {

    /**
     * Send a sensor snapshot to the backend.
     */
    @POST("diagnostics/{diagnosticUUID}/snapshots")
    suspend fun createSensorSnapshot(
        @Path("diagnosticUUID") diagnosticUUID: String,
        @Body request: CreateSensorSnapshotRequest
    ): Response<SensorSnapshotResponse>
} 