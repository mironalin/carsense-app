package com.carsense.features.sensors.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request model for individual sensor reading in a snapshot.
 */
@JsonClass(generateAdapter = true)
data class SensorReadingRequest(
    val pid: String,
    val value: Double,
    val unit: String,
    val timestamp: String
)

/**
 * Request model for creating a sensor snapshot with readings.
 */
@JsonClass(generateAdapter = true)
data class CreateSensorSnapshotRequest(
    val source: String = "obd2",
    val readings: List<SensorReadingRequest>
)

/**
 * Response model for a sensor snapshot.
 */
@JsonClass(generateAdapter = true)
data class SensorSnapshotDto(
    val uuid: String,
    @Json(name = "diagnosticUUID") val diagnosticUUID: String,
    val source: String,
    @Json(name = "createdAt") val createdAt: String,
    @Json(name = "updatedAt") val updatedAt: String
)

/**
 * Response model for individual sensor reading.
 */
@JsonClass(generateAdapter = true)
data class SensorReadingDto(
    val uuid: String?,
    @Json(name = "sensorSnapshotsUUID") val sensorSnapshotsUUID: String?,
    val pid: String,
    val value: Double,
    val unit: String,
    val timestamp: String?
)

/**
 * Complete response from creating a sensor snapshot.
 */
@JsonClass(generateAdapter = true)
data class SensorSnapshotResponse(
    val snapshot: SensorSnapshotDto,
    val readings: List<SensorReadingDto>
) 