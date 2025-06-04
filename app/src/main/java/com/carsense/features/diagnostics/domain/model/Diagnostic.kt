package com.carsense.features.diagnostics.domain.model

import java.time.OffsetDateTime

/**
 * Domain model representing a diagnostic session for a vehicle.
 * This serves as the root entity for diagnostic data collection, including
 * DTCs, sensor readings, and location data.
 */
data class Diagnostic(
    val uuid: String,
    val vehicleUuid: String,
    val odometer: Int,
    val locationLat: Double?,
    val locationLong: Double?,
    val notes: String?,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime
) 