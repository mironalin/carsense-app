package com.carsense.features.sensors.data.repository

import android.util.Log
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.sensors.domain.command.CoolantTemperatureCommand
import com.carsense.features.sensors.domain.command.FuelLevelCommand
import com.carsense.features.sensors.domain.command.RPMCommand
import com.carsense.features.sensors.domain.command.SensorCommand
import com.carsense.features.sensors.domain.command.SpeedCommand
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.domain.repository.SensorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of SensorRepository that communicates with the vehicle's OBD system */
@Singleton
class SensorRepositoryImpl
@Inject
constructor(private val bluetoothController: BluetoothController) : SensorRepository {

    private val TAG = "SensorRepository"

    // Registry of available sensor commands
    private val sensorCommands = mutableMapOf<String, SensorCommand>()

    // Cache of the latest readings
    private val latestReadings = mutableMapOf<String, SensorReading>()

    // Shared flow of all sensor readings
    private val _sensorReadings = MutableSharedFlow<SensorReading>(replay = 0)

    // Flag to track if monitoring is active
    private var isMonitoring = false

    // Coroutine job for the monitoring loop
    private var monitoringJob: Job? = null

    init {
        // Register the available sensor commands
        registerSensorCommands()
    }

    /** Register all available sensor commands */
    private fun registerSensorCommands() {
        val commands =
            listOf(
                RPMCommand(),
                SpeedCommand(),
                CoolantTemperatureCommand(),
                FuelLevelCommand()
            )

        commands.forEach { command -> sensorCommands[command.pid] = command }

        Log.d(TAG, "Registered ${sensorCommands.size} sensor commands")
    }

    override fun getSensorReadings(sensorId: String): Flow<SensorReading> {
        return _sensorReadings.asSharedFlow().filter { it.pid == sensorId }
    }

    override suspend fun getLatestReading(sensorId: String): SensorReading? {
        return latestReadings[sensorId]
    }

    override suspend fun requestReading(sensorId: String): Result<SensorReading> {
        return try {
            val command =
                sensorCommands[sensorId]
                    ?: throw IllegalArgumentException("Sensor not found: $sensorId")

            if (!bluetoothController.isConnected.value) {
                Log.e(TAG, "Not connected to the vehicle")
                return Result.failure(IllegalStateException("Not connected to the vehicle"))
            }

            val response = bluetoothController.sendOBD2Command(command.getCommand())
            if (response == null) {
                Log.e(TAG, "No response received for sensor $sensorId")
                return Result.failure(IllegalStateException("No response received"))
            }

            val reading = command.parseResponse(response.content)

            // Update the latest reading cache
            latestReadings[sensorId] = reading

            // Emit the reading to the flow
            _sensorReadings.emit(reading)

            Result.success(reading)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting reading for sensor $sensorId", e)
            Result.failure(e)
        }
    }

    override suspend fun getAvailableSensors(): List<String> {
        return sensorCommands.keys.toList()
    }

    override suspend fun startMonitoring(updateIntervalMs: Long) {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already active")
            return
        }

        isMonitoring = true

        // Start the monitoring coroutine
        monitoringJob =
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Starting sensor monitoring")

                while (isActive && isMonitoring) {
                    if (!bluetoothController.isConnected.value) {
                        Log.d(TAG, "Not connected, skipping sensor updates")
                        delay(1000) // Wait before checking connection again
                        continue
                    }

                    // Request readings for all sensors
                    for (sensorId in sensorCommands.keys) {
                        try {
                            requestReading(sensorId)
                            // Small delay between commands to not overwhelm the OBD adapter
                            delay(100)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error monitoring sensor $sensorId", e)
                        }
                    }

                    // Wait for the next update interval
                    delay(updateIntervalMs)
                }

                Log.d(TAG, "Sensor monitoring stopped")
            }
    }

    override suspend fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null

        Log.d(TAG, "Stopped sensor monitoring")
    }
}
