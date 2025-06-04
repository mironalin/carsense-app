package com.carsense.features.diagnostics.domain.usecase

import android.util.Log
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.diagnostics.domain.model.Diagnostic
import com.carsense.features.vehicles.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/**
 * Use case for creating a diagnostic after successful Bluetooth OBD2 connection
 */
class CreateDiagnosticAfterConnectionUseCase @Inject constructor(
    private val bluetoothController: BluetoothController,
    private val createDiagnosticUseCase: CreateDiagnosticUseCase,
    private val vehicleRepository: VehicleRepository
) {
    private val TAG = "CreateDiagnosticAfterConnection"

    /**
     * Creates a diagnostic record after successful Bluetooth connection
     * @param odometer The vehicle's current odometer reading
     * @return Flow emitting the created Diagnostic on success, or an error on failure
     */
    fun execute(odometer: Int): Flow<Result<Diagnostic>> = flow {
        try {
            Log.d(TAG, "Starting diagnostic creation with odometer: $odometer")

            // Make sure we're actually connected
            val isConnected = bluetoothController.isConnected.value
            Log.d(TAG, "Checking connection status: isConnected = $isConnected")

            if (!isConnected) {
                Log.e(TAG, "Error: Not connected to OBD2 adapter")
                emit(Result.failure(Exception("Not connected to OBD2 adapter")))
                return@flow
            }

            // Get the currently selected vehicle
            Log.d(TAG, "Getting selected vehicle from repository")
            val vehicles = vehicleRepository.getAllVehicles().firstOrNull()
            Log.d(TAG, "Retrieved ${vehicles?.size ?: 0} vehicles from repository")

            val selectedVehicle = vehicles?.find { it.isSelected }

            if (selectedVehicle == null) {
                Log.e(TAG, "Error: No vehicle selected")
                emit(Result.failure(Exception("No vehicle selected")))
                return@flow
            }

            Log.d(
                TAG,
                "Selected vehicle found: ${selectedVehicle.make} ${selectedVehicle.model} (${selectedVehicle.uuid})"
            )

            // Create the diagnostic record
            Log.d(
                TAG,
                "Creating diagnostic record for vehicle ${selectedVehicle.uuid} with odometer $odometer"
            )
            val result = createDiagnosticUseCase(
                vehicleUuid = selectedVehicle.uuid,
                odometer = odometer
            )

            if (result.isSuccess) {
                Log.d(TAG, "Diagnostic created successfully with UUID: ${result.getOrNull()?.uuid}")
            } else {
                Log.e(TAG, "Failed to create diagnostic: ${result.exceptionOrNull()?.message}")
            }

            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating diagnostic after connection: ${e.message}", e)
            emit(Result.failure(e))
        }
    }.catch { e ->
        Log.e(TAG, "Exception in diagnostic creation flow: ${e.message}", e)
        emit(Result.failure(e))
    }
} 