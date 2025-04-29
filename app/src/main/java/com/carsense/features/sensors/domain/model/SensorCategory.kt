package com.carsense.features.sensors.domain.model

/**
 * Represents a category of related sensors
 *
 * @property id Unique identifier for the category
 * @property name User-friendly name for the category
 * @property description Brief description of the sensors in this category
 * @property sensorPids List of PIDs belonging to this category
 */
data class SensorCategory(
    val id: String,
    val name: String,
    val description: String,
    val sensorPids: List<String>
)

/** Defines standard sensor categories based on the Sensor Implementation Roadmap */
object SensorCategories {
    val ENGINE_PERFORMANCE =
        SensorCategory(
            id = "engine_performance",
            name = "Engine Performance",
            description = "Sensors related to engine operation and performance",
            sensorPids =
                listOf(
                    "0C", // RPM
                    "04", // Engine Load
                    "11" // Throttle Position
                )
        )

    val VEHICLE_PERFORMANCE =
        SensorCategory(
            id = "vehicle_performance",
            name = "Vehicle Performance",
            description = "Sensors related to vehicle movement and performance",
            sensorPids =
                listOf(
                    "0D", // Vehicle Speed
                    "10" // MAF Air Flow Rate
                )
        )

    val TEMPERATURE =
        SensorCategory(
            id = "temperature",
            name = "Temperature",
            description = "Temperature sensors from various vehicle systems",
            sensorPids =
                listOf(
                    "05", // Engine Coolant Temperature
                    "0F" // Intake Air Temperature
                )
        )

    val FUEL_SYSTEM =
        SensorCategory(
            id = "fuel_system",
            name = "Fuel System",
            description = "Sensors related to the fuel delivery system",
            sensorPids =
                listOf(
                    "2F", // Fuel Level
                    "0A" // Fuel Pressure
                )
        )

    /** Get all available categories */
    fun getAllCategories(): List<SensorCategory> =
        listOf(ENGINE_PERFORMANCE, VEHICLE_PERFORMANCE, TEMPERATURE, FUEL_SYSTEM)

    /** Get a category by its ID */
    fun getCategoryById(id: String): SensorCategory? = getAllCategories().find { it.id == id }

    /** Get all categories that contain a specific PID */
    fun getCategoriesForPid(pid: String): List<SensorCategory> =
        getAllCategories().filter { category -> category.sensorPids.contains(pid) }
}
