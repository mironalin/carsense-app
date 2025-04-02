package com.carsense.features.obd2.data

import com.carsense.features.obd2.domain.model.SensorReading

/** Represents a parsed response from the OBD2 adapter */
data class OBD2Response(
        val command: String,
        val rawData: String,
        val decodedValue: String,
        val unit: String,
        val isError: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
) {
    /** Converts this OBD2Response to a SensorReading */
    fun toSensorReading(displayName: String, mode: Int, pid: String): SensorReading {
        return SensorReading(
                name = displayName,
                value = decodedValue,
                unit = unit,
                pid = pid,
                mode = mode,
                timestamp = timestamp,
                rawValue = rawData,
                isError = isError
        )
    }

    companion object {
        /** Creates an error response */
        fun createError(command: String, errorMessage: String, rawData: String = ""): OBD2Response {
            return OBD2Response(
                    command = command,
                    rawData = rawData,
                    decodedValue = errorMessage,
                    unit = "",
                    isError = true
            )
        }

        /** Converts a SensorReading to an OBD2Response */
        fun fromSensorReading(reading: SensorReading, command: String): OBD2Response {
            return OBD2Response(
                    command = command,
                    rawData = reading.rawValue,
                    decodedValue = reading.value,
                    unit = reading.unit,
                    isError = reading.isError,
                    timestamp = reading.timestamp
            )
        }
    }
}
