package com.carsense.core.repository

import com.carsense.core.model.Vehicle
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for vehicle operations.
 * Handles both local database and remote API operations.
 */
interface VehicleRepository {
    /**
     * Get all vehicles from local database and/or remote API.
     */
    fun getAllVehicles(): Flow<List<Vehicle>>

    /**
     * Get a vehicle by UUID.
     */
    suspend fun getVehicleByUuid(uuid: String): Result<Vehicle>

    /**
     * Create a new vehicle.
     */
    suspend fun createVehicle(vehicle: Vehicle): Result<Vehicle>

    /**
     * Update an existing vehicle.
     */
    suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle>

    /**
     * Delete a vehicle.
     */
    suspend fun deleteVehicle(uuid: String): Result<Boolean>

    /**
     * Restore a deleted vehicle.
     */
    suspend fun restoreVehicle(uuid: String): Result<Vehicle>

    /**
     * Refresh vehicles from the remote API.
     */
    suspend fun refreshVehicles(): Result<Boolean>

    /**
     * Select a vehicle as the current/active vehicle.
     */
    suspend fun selectVehicle(uuid: String): Result<Boolean>

    /**
     * Get the currently selected vehicle.
     */
    suspend fun getSelectedVehicle(): Result<Vehicle?>

    /**
     * Check if a vehicle is currently selected.
     */
    fun isVehicleSelected(): Flow<Boolean>
} 