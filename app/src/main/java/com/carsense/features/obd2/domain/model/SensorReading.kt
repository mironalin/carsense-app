package com.carsense.features.obd2.domain.model

/** Represents a reading from a vehicle sensor */
data class SensorReading(
    /** Human-readable name of the parameter */
    val name: String,

    /** String representation of the value */
    val value: String,

    /** Unit of measurement (e.g., "km/h", "°C") */
    val unit: String,

    /** Parameter ID */
    val pid: String,

    /** OBD mode */
    val mode: Int,

    /** Timestamp of the reading in milliseconds */
    val timestamp: Long,

    /** Raw value received from the OBD adapter */
    val rawValue: String,

    /** Whether this reading represents an error */
    val isError: Boolean = false
) {
    /** Formatted string representation with unit */
    val formattedValue: String
        get() = if (unit.isNotEmpty()) "$value $unit" else value

    /** Time since the reading was taken, in milliseconds */
    fun getAge(): Long {
        return System.currentTimeMillis() - timestamp
    }

    /** Check if the reading is stale (older than given threshold) */
    fun isStale(thresholdMs: Long): Boolean {
        return getAge() > thresholdMs
    }
}
