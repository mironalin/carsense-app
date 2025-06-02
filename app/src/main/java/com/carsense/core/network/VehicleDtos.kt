package com.carsense.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VehicleDto(
    val id: Int,
    val uuid: String,
    @Json(name = "ownerId") val ownerId: String,
    val vin: String,
    val make: String,
    val model: String,
    val year: Int,
    @Json(name = "engineType") val engineType: String,
    @Json(name = "fuelType") val fuelType: String,
    @Json(name = "transmissionType") val transmissionType: String,
    val drivetrain: String,
    @Json(name = "licensePlate") val licensePlate: String,
    @Json(name = "odometerUpdatedAt") val odometerUpdatedAt: String?,
    @Json(name = "deletedAt") val deletedAt: String?,
    @Json(name = "createdAt") val createdAt: String,
    @Json(name = "updatedAt") val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class CreateVehicleRequest(
    val vin: String,
    val make: String,
    val model: String,
    val year: Int,
    @Json(name = "engineType") val engineType: String,
    @Json(name = "fuelType") val fuelType: String,
    @Json(name = "transmissionType") val transmissionType: String,
    val drivetrain: String,
    @Json(name = "licensePlate") val licensePlate: String,
    @Json(name = "deletedAt") val deletedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateVehicleResponse(
    val vehicle: VehicleDto, val created: Boolean
)

@JsonClass(generateAdapter = true)
data class UpdateVehicleRequest(
    val ownerId: String? = null,
    val vin: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    @Json(name = "engineType") val engineType: String? = null,
    @Json(name = "fuelType") val fuelType: String? = null,
    @Json(name = "transmissionType") val transmissionType: String? = null,
    val drivetrain: String? = null,
    @Json(name = "licensePlate") val licensePlate: String? = null,
    @Json(name = "odometerUpdatedAt") val odometerUpdatedAt: String? = null,
    @Json(name = "deletedAt") val deletedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteVehicleResponse(
    val message: String, @Json(name = "vehicleUUID") val vehicleUUID: String
)

@JsonClass(generateAdapter = true)
data class RestoreVehicleResponse(
    val vehicle: VehicleDto, val restored: Boolean
) 