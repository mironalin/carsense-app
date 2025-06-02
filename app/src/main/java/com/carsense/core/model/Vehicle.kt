package com.carsense.core.model

/**
 * Domain model for a Vehicle.
 */
data class Vehicle(
    val id: Int = 0,
    val uuid: String = "",
    val ownerId: String = "",
    val vin: String = "",
    val make: String = "",
    val model: String = "",
    val year: Int = 0,
    val engineType: String = "",
    val fuelType: String = "",
    val transmissionType: String = "",
    val drivetrain: String = "",
    val licensePlate: String = "",
    val odometerUpdatedAt: String? = null,
    val deletedAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val isSelected: Boolean = false // Track if this is the currently selected vehicle
) 