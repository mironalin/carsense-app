package com.carsense.features.diagnostics.data.repository

import android.util.Log
import com.carsense.features.diagnostics.data.api.CreateDiagnosticRequest
import com.carsense.features.diagnostics.data.api.DiagnosticDto
import com.carsense.features.diagnostics.data.api.DiagnosticsApiService
import com.carsense.features.diagnostics.domain.model.Diagnostic
import com.carsense.features.diagnostics.domain.repository.DiagnosticRepository
import com.carsense.features.vehicles.data.api.VehicleApiService
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticRepositoryImpl @Inject constructor(
    private val diagnosticsApiService: DiagnosticsApiService,
    private val vehicleApiService: VehicleApiService
) : DiagnosticRepository {

    private val TAG = "DiagnosticRepository"

    override suspend fun createDiagnostic(
        vehicleUuid: String,
        odometer: Int,
        locationLat: Double?,
        locationLong: Double?,
        notes: String?
    ): Result<Diagnostic> {
        Log.d(TAG, "Creating diagnostic for vehicle $vehicleUuid with odometer $odometer")
        return try {
            // Create the diagnostic using the vehicle UUID directly
            val request = CreateDiagnosticRequest(
                vehicleUUID = vehicleUuid,
                odometer = odometer,
                locationLat = locationLat,
                locationLong = locationLong,
                notes = notes
            )

            Log.d(TAG, "Sending API request to create diagnostic: $request")
            val response = diagnosticsApiService.createDiagnostic(request)
            Log.d(
                TAG,
                "Received response: isSuccessful=${response.isSuccessful}, code=${response.code()}"
            )

            if (response.isSuccessful && response.body() != null) {
                val diagnosticDto = response.body()!!
                Log.d(TAG, "Diagnostic created successfully with UUID: ${diagnosticDto.uuid}")
                Result.success(mapToDomain(diagnosticDto))
            } else {
                val errorMsg =
                    "Failed to create diagnostic: ${response.code()} - ${response.message()}"
                Log.e(TAG, "$errorMsg, errorBody: ${response.errorBody()?.string()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating diagnostic: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getDiagnosticByUuid(uuid: String): Result<Diagnostic> {
        return try {
            val response = diagnosticsApiService.getDiagnosticByUuid(uuid)
            if (response.isSuccessful && response.body() != null) {
                val diagnosticDto = response.body()!!
                Result.success(mapToDomain(diagnosticDto))
            } else {
                val errorMsg = "Failed to fetch diagnostic: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching diagnostic: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getAllDiagnostics(): Result<List<Diagnostic>> {
        return try {
            val response = diagnosticsApiService.getAllDiagnostics()
            if (response.isSuccessful && response.body() != null) {
                val diagnosticDtos = response.body()!!
                val diagnostics = diagnosticDtos.map { mapToDomain(it) }
                Result.success(diagnostics)
            } else {
                val errorMsg = "Failed to fetch diagnostics: ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching diagnostics: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Maps a DTO from the API to a domain model
     */
    private fun mapToDomain(dto: DiagnosticDto): Diagnostic {
        return Diagnostic(
            uuid = dto.uuid,
            vehicleUuid = dto.vehicleUUID,
            odometer = dto.odometer,
            locationLat = dto.locationLat,
            locationLong = dto.locationLong,
            notes = dto.notes,
            createdAt = dto.createdAt?.let { parseDateTime(it) },
            updatedAt = parseDateTime(dto.updatedAt)
        )
    }

    /**
     * Parses an ISO-8601 datetime string to OffsetDateTime
     */
    private fun parseDateTime(dateTimeStr: String): OffsetDateTime {
        return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
} 