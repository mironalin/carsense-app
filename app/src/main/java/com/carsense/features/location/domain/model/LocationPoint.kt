package com.carsense.features.location.domain.model

/**
 * Domain model representing a location point tracked by the app.
 * This is used by LocationTracker and ForegroundLocationService to track vehicle location.
 */
data class LocationPoint(
    val uuid: String,
    val vehicleUUID: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    var speed: Float? = null,
    val accuracy: Float? = null,
    val timestamp: Long,
    val isSynced: Boolean = false
) 