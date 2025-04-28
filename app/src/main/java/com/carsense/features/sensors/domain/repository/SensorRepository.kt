package com.carsense.features.sensors.domain.repository

import com.carsense.features.sensors.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow

/** Repository for accessing vehicle sensor data */
interface SensorRepository {
    /**
     * Gets a real-time flow of readings for the requested sensor
     * @param sensorId The identifier for the sensor (typically the PID)
     * @return Flow of sensor readings
     */
    fun getSensorReadings(sensorId: String): Flow<SensorReading>

    /**
     * Gets the most recent reading for a specific sensor
     * @param sensorId The identifier for the sensor (typically the PID)
     * @return The most recent sensor reading, or null if no reading is available
     */
    suspend fun getLatestReading(sensorId: String): SensorReading?

    /**
     * Gets a single reading for a sensor (on-demand)
     * @param sensorId The identifier for the sensor
     * @return The sensor reading result
     */
    suspend fun requestReading(sensorId: String): Result<SensorReading>

    /**
     * Gets the list of available sensors
     * @return List of available sensor IDs
     */
    suspend fun getAvailableSensors(): List<String>

    /**
     * Starts monitoring all sensors
     * @param updateIntervalMs The interval between sensor updates in milliseconds
     */
    suspend fun startMonitoring(updateIntervalMs: Long = 1000)

    /** Stops monitoring sensors */
    suspend fun stopMonitoring()
}
