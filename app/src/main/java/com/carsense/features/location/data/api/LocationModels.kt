package com.carsense.features.location.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for creating a location entry.
 * This matches the zBulkLocationInsertSchema from the backend API.
 */
@JsonClass(generateAdapter = true)
data class CreateLocationRequest(
    val vehicleUUID: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speed: Double? = null,
    val accuracy: Double? = null,
    val timestamp: String? = null // ISO 8601 timestamp from client
)

/**
 * Response from the bulk location creation endpoint.
 * This matches the zBulkLocationsResponseSchema from the backend API.
 */
@JsonClass(generateAdapter = true)
data class CreateBulkLocationsResponse(
    val message: String,
    val count: Int,
    val locations: List<LocationDto>
)

/**
 * Data Transfer Object for a location received from the API.
 * This matches the location schema from the backend API.
 */
@JsonClass(generateAdapter = true)
data class LocationDto(
    val uuid: String,
    val diagnosticUUID: String,
    val vehicleUUID: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Double?,
    val accuracy: Double?,
    @Json(name = "timestamp") val timestamp: String
) 