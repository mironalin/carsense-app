package com.carsense.features.diagnostics.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for diagnostic records from the API.
 */
@JsonClass(generateAdapter = true)
data class DiagnosticDto(
    val uuid: String,
    @Json(name = "vehicleUUID") val vehicleUUID: String,
    val odometer: Int,
    @Json(name = "locationLat") val locationLat: Double?,
    @Json(name = "locationLong") val locationLong: Double?,
    val notes: String?,
    @Json(name = "createdAt") val createdAt: String?,
    @Json(name = "updatedAt") val updatedAt: String
)

/**
 * Request body for creating a new diagnostic record.
 */
@JsonClass(generateAdapter = true)
data class CreateDiagnosticRequest(
    @Json(name = "vehicleUUID") val vehicleUUID: String,
    val odometer: Int,
    @Json(name = "locationLat") val locationLat: Double? = null,
    @Json(name = "locationLong") val locationLong: Double? = null,
    val notes: String? = null
) 