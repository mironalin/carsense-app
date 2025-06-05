package com.carsense.features.sensors.domain.repository

import com.carsense.features.sensors.domain.model.SensorCategory
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
     * Gets the list of all available sensors
     * @return List of available sensor IDs
     */
    suspend fun getAvailableSensors(): List<String>

    /**
     * Gets the list of sensors supported by the vehicle
     * @return List of supported sensor IDs
     */
    suspend fun getSupportedSensors(): List<String>

    /**
     * Gets the list of available sensor categories
     * @return List of sensor categories
     */
    suspend fun getSensorCategories(): List<SensorCategory>

    /**
     * Gets the sensors in a specific category
     * @param categoryId The category identifier
     * @return List of sensor IDs in the category
     */
    suspend fun getSensorsInCategory(categoryId: String): List<String>

    /**
     * Detects which sensors are supported by the vehicle
     * @param forceRefresh Whether to force a refresh even if detection has already been run
     * @return True if detection was successful, false otherwise
     */
    suspend fun detectSupportedSensors(forceRefresh: Boolean = false): Boolean

    /**
     * Starts monitoring all sensors
     * @param updateIntervalMs The interval between sensor updates in milliseconds
     */
    suspend fun startMonitoring(updateIntervalMs: Long = 1000)

    /**
     * Starts monitoring specific sensors
     * @param sensorIds List of sensor IDs to monitor
     * @param updateIntervalMs The interval between sensor updates in milliseconds
     */
    suspend fun startMonitoringSensors(sensorIds: List<String>, updateIntervalMs: Long = 1000)

    /** Stops monitoring sensors */
    suspend fun stopMonitoring()

    /**
     * Starts monitoring specific sensors with priority-based refresh rates
     * @param highPrioritySensors List of high priority sensor IDs (updated most frequently)
     * @param mediumPrioritySensors List of medium priority sensor IDs (updated at medium frequency)
     * @param lowPrioritySensors List of low priority sensor IDs (updated least frequently)
     * @param baseRefreshRateMs The base interval for high priority sensors in milliseconds
     */
    suspend fun startPrioritizedMonitoring(
        highPrioritySensors: List<String>,
        mediumPrioritySensors: List<String>,
        lowPrioritySensors: List<String>,
        baseRefreshRateMs: Long = 200
    )
}
