package com.carsense.features.vehicles.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.carsense.features.vehicles.data.api.CreateVehicleRequest
import com.carsense.features.vehicles.data.api.UpdateVehicleRequest
import com.carsense.features.vehicles.data.api.VehicleApiService
import com.carsense.features.vehicles.data.api.VehicleDto
import com.carsense.features.vehicles.data.dataStore
import com.carsense.features.vehicles.data.db.VehicleDao
import com.carsense.features.vehicles.data.db.VehicleEntity
import com.carsense.features.vehicles.domain.model.Vehicle
import com.carsense.features.vehicles.domain.repository.VehicleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleDao: VehicleDao,
    private val vehicleApiService: VehicleApiService
) : VehicleRepository {

    private val selectedVehicleKey = stringPreferencesKey("selected_vehicle_uuid")

    override fun getAllVehicles(): Flow<List<Vehicle>> {
        return combine(
            vehicleDao.getAllVehicles(), context.dataStore.data.catch { e ->
                Timber.Forest.e(e, "Error reading preferences")
                emit(emptyPreferences())
            }) { vehicles, preferences ->
            val selectedVehicleUuid = preferences[selectedVehicleKey]
            vehicles.map { entity ->
                entity.toDomainModel(isSelected = entity.uuid == selectedVehicleUuid)
            }
        }
    }

    override suspend fun getVehicleByUuid(uuid: String): Result<Vehicle> {
        return try {
            val localVehicle = vehicleDao.getVehicleByUuid(uuid)
            if (localVehicle != null) {
                val selectedVehicleUuid =
                    context.dataStore.data.firstOrNull()?.get(selectedVehicleKey)
                Result.success(localVehicle.toDomainModel(isSelected = uuid == selectedVehicleUuid))
            } else {
                // Try to get from API if not in local DB
                val response = vehicleApiService.getVehicleByUuid(uuid)
                if (response.isSuccessful && response.body() != null) {
                    val vehicleDto = response.body()!!
                    val entity = vehicleDto.toEntity()
                    vehicleDao.insert(entity)

                    val selectedVehicleUuid =
                        context.dataStore.data.firstOrNull()?.get(selectedVehicleKey)
                    Result.success(entity.toDomainModel(isSelected = uuid == selectedVehicleUuid))
                } else {
                    Result.failure(Exception("Failed to fetch vehicle: ${response.message()}"))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting vehicle $uuid")
            Result.failure(e)
        }
    }

    override suspend fun createVehicle(vehicle: Vehicle): Result<Vehicle> {
        return try {
            val request = CreateVehicleRequest(
                vin = vehicle.vin,
                make = vehicle.make,
                model = vehicle.model,
                year = vehicle.year,
                engineType = vehicle.engineType,
                fuelType = vehicle.fuelType,
                transmissionType = vehicle.transmissionType,
                drivetrain = vehicle.drivetrain,
                licensePlate = vehicle.licensePlate
            )

            val response = vehicleApiService.createVehicle(request)
            if (response.isSuccessful && response.body() != null) {
                val vehicleDto = response.body()!!.vehicle
                val entity = vehicleDto.toEntity()
                vehicleDao.insert(entity)
                Result.success(entity.toDomainModel())
            } else {
                Result.failure(Exception("Failed to create vehicle: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error creating vehicle")
            Result.failure(e)
        }
    }

    override suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle> {
        return try {
            val request = UpdateVehicleRequest(
                vin = vehicle.vin,
                make = vehicle.make,
                model = vehicle.model,
                year = vehicle.year,
                engineType = vehicle.engineType,
                fuelType = vehicle.fuelType,
                transmissionType = vehicle.transmissionType,
                drivetrain = vehicle.drivetrain,
                licensePlate = vehicle.licensePlate
            )

            val response = vehicleApiService.updateVehicle(vehicle.uuid, request)
            if (response.isSuccessful && response.body() != null) {
                val vehicleDto = response.body()!!
                val entity = vehicleDto.toEntity()
                vehicleDao.update(entity)
                Result.success(entity.toDomainModel())
            } else {
                Result.failure(Exception("Failed to update vehicle: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating vehicle ${vehicle.uuid}")
            Result.failure(e)
        }
    }

    override suspend fun deleteVehicle(uuid: String): Result<Boolean> {
        return try {
            val response = vehicleApiService.deleteVehicle(uuid)
            if (response.isSuccessful) {
                // Remove from local DB only if API succeeds
                vehicleDao.deleteByUuid(uuid)

                // If this was the selected vehicle, clear the selection
                val selectedVehicleUuid =
                    context.dataStore.data.firstOrNull()?.get(selectedVehicleKey)
                if (selectedVehicleUuid == uuid) {
                    context.dataStore.edit { preferences ->
                        preferences.remove(selectedVehicleKey)
                    }
                }

                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete vehicle: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting vehicle $uuid")
            Result.failure(e)
        }
    }

    override suspend fun restoreVehicle(uuid: String): Result<Vehicle> {
        return try {
            val response = vehicleApiService.restoreVehicle(uuid)
            if (response.isSuccessful && response.body() != null) {
                val vehicleDto = response.body()!!.vehicle
                val entity = vehicleDto.toEntity()
                vehicleDao.insert(entity)
                Result.success(entity.toDomainModel())
            } else {
                Result.failure(Exception("Failed to restore vehicle: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error restoring vehicle $uuid")
            Result.failure(e)
        }
    }

    override suspend fun refreshVehicles(): Result<Boolean> {
        return try {
            Timber.d("refreshVehicles: Attempting to fetch vehicles from API")
            val response = vehicleApiService.getAllVehicles()
            if (response.isSuccessful && response.body() != null) {
                val vehicleDtos = response.body()!!
                Timber.d("refreshVehicles: Successfully fetched ${vehicleDtos.size} vehicles from API")

                // Get current vehicles in database to determine which ones to remove
                val existingVehicles = vehicleDao.getAllVehiclesSync()
                val apiVehicleUuids = vehicleDtos.map { it.uuid }.toSet()
                val vehiclesToRemove = existingVehicles.filter { it.uuid !in apiVehicleUuids }

                // If API returns empty list, clear all local vehicles
                if (vehicleDtos.isEmpty()) {
                    Timber.d("refreshVehicles: Clearing all local vehicles as API returned empty list")
                    vehicleDao.clearAll()

                    // Clear selected vehicle
                    context.dataStore.edit { preferences ->
                        preferences.remove(selectedVehicleKey)
                    }
                } else {
                    // Insert/update all vehicles from API
                    for (dto in vehicleDtos) {
                        val entity = dto.toEntity()
                        val existing = vehicleDao.getVehicleByUuid(dto.uuid)
                        if (existing != null) {
                            vehicleDao.update(entity)
                            Timber.d("refreshVehicles: Updated vehicle ${dto.make} ${dto.model} (${dto.uuid})")
                        } else {
                            vehicleDao.insert(entity)
                            Timber.d("refreshVehicles: Inserted new vehicle ${dto.make} ${dto.model} (${dto.uuid})")
                        }
                    }

                    // Remove vehicles that exist locally but not in API response
                    if (vehiclesToRemove.isNotEmpty()) {
                        Timber.d("refreshVehicles: Removing ${vehiclesToRemove.size} vehicles that no longer exist in API")
                        for (entity in vehiclesToRemove) {
                            vehicleDao.deleteByUuid(entity.uuid)

                            // If this was the selected vehicle, clear the selection
                            val selectedVehicleUuid =
                                context.dataStore.data.firstOrNull()?.get(selectedVehicleKey)
                            if (selectedVehicleUuid == entity.uuid) {
                                context.dataStore.edit { preferences ->
                                    preferences.remove(selectedVehicleKey)
                                }
                            }
                        }
                    }
                }

                Result.success(true)
            } else {
                Timber.e("refreshVehicles: API call failed with code ${response.code()}: ${response.message()}")
                Result.failure(Exception("Failed to refresh vehicles: ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "refreshVehicles: Error refreshing vehicles")
            Result.failure(e)
        }
    }

    override suspend fun selectVehicle(uuid: String): Result<Boolean> {
        return try {
            // Verify the vehicle exists before selecting it
            val vehicle = vehicleDao.getVehicleByUuid(uuid)
            if (vehicle != null) {
                context.dataStore.edit { preferences ->
                    preferences[selectedVehicleKey] = uuid
                }
                Result.success(true)
            } else {
                Result.failure(Exception("Vehicle not found with UUID: $uuid"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error selecting vehicle $uuid")
            Result.failure(e)
        }
    }

    override suspend fun getSelectedVehicle(): Result<Vehicle?> {
        return try {
            val selectedVehicleUuid = context.dataStore.data.firstOrNull()?.get(selectedVehicleKey)
            if (selectedVehicleUuid != null) {
                val vehicle = vehicleDao.getVehicleByUuid(selectedVehicleUuid)
                if (vehicle != null) {
                    Result.success(vehicle.toDomainModel(isSelected = true))
                } else {
                    // If the selected vehicle is not in local DB, try to get from API
                    try {
                        val response = vehicleApiService.getVehicleByUuid(selectedVehicleUuid)
                        if (response.isSuccessful && response.body() != null) {
                            val vehicleDto = response.body()!!
                            val entity = vehicleDto.toEntity()
                            vehicleDao.insert(entity)
                            Result.success(entity.toDomainModel(isSelected = true))
                        } else {
                            // Clear selected vehicle since it doesn't exist
                            context.dataStore.edit { preferences ->
                                preferences.remove(selectedVehicleKey)
                            }
                            Result.success(null)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error fetching selected vehicle from API")
                        Result.success(null) // Return null rather than failure
                    }
                }
            } else {
                Result.success(null) // No vehicle is selected
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting selected vehicle")
            Result.failure(e)
        }
    }

    override fun isVehicleSelected(): Flow<Boolean> {
        return context.dataStore.data.catch { e ->
            Timber.Forest.e(e, "Error reading preferences")
            emit(emptyPreferences())
        }.map { preferences ->
            val uuid = preferences[selectedVehicleKey]
            !uuid.isNullOrEmpty()
        }
    }

    // Extension functions to convert between models

    private fun VehicleDto.toEntity(): VehicleEntity {
        return VehicleEntity(
            uuid = this.uuid,
            userId = this.ownerId,
            vehicleIdentificationNumber = this.vin,
            make = this.make,
            model = this.model,
            year = this.year.toString(),
            engineDisplacement = null, // Not provided by API
            engineType = this.engineType,
            fuelType = this.fuelType,
            transmissionType = this.transmissionType,
            drivetrain = this.drivetrain,
            licensePlate = this.licensePlate,
            isSynced = true // From API, so already synced
        )
    }

    private fun VehicleEntity.toDomainModel(isSelected: Boolean = false): Vehicle {
        return Vehicle(
            uuid = uuid,
            ownerId = userId ?: "",
            vin = vehicleIdentificationNumber ?: "",
            make = make ?: "",
            model = model ?: "",
            year = year?.toIntOrNull() ?: 0,
            engineType = engineType ?: "",
            fuelType = fuelType ?: "",
            transmissionType = transmissionType ?: "",
            drivetrain = drivetrain ?: "",
            licensePlate = licensePlate ?: "",
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString(),
            isSelected = isSelected
        )
    }
}