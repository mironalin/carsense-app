package com.carsense.features.dtc.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for creating a diagnostic trouble code (DTC) entry.
 */
@JsonClass(generateAdapter = true)
data class CreateDTCRequest(
    val code: String,
    val confirmed: Boolean = true // Default to true for Mode 03 DTCs
)

/**
 * Response from the DTC creation endpoint.
 */
@JsonClass(generateAdapter = true)
data class CreateDTCsResponse(
    val message: String,
    val count: Int,
    val dtcs: List<DTCDto>
)

/**
 * Data Transfer Object for a DTC received from the API.
 */
@JsonClass(generateAdapter = true)
data class DTCDto(
    val uuid: String,
    val diagnosticUUID: String,
    val code: String,
    val confirmed: Boolean?,
    @Json(name = "createdAt") val createdAt: String,
    @Json(name = "updatedAt") val updatedAt: String
) 