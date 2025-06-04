package com.carsense.features.diagnostics.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * API Service interface for Diagnostics related endpoints.
 */
interface DiagnosticsApiService {

    /**
     * Get all diagnostics records for the user's vehicles.
     */
    @GET("diagnostics")
    suspend fun getAllDiagnostics(): Response<List<DiagnosticDto>>

    /**
     * Get a specific diagnostic by UUID.
     */
    @GET("diagnostics/{diagnosticUUID}")
    suspend fun getDiagnosticByUuid(@Path("diagnosticUUID") uuid: String): Response<DiagnosticDto>

    /**
     * Create a new diagnostic record.
     */
    @POST("diagnostics")
    suspend fun createDiagnostic(@Body request: CreateDiagnosticRequest): Response<DiagnosticDto>
} 