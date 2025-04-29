package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the vehicle speed */
class SpeedCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.VEHICLE_SPEED
    override val displayName: String = "Vehicle Speed"
    override val displayDescription: String = "Current vehicle speed"
    override val minValue: Float = SensorConstants.Range.SPEED.start
    override val maxValue: Float = SensorConstants.Range.SPEED.endInclusive
    override val unit: String = "km/h"

    /**
     * Parses the raw response string into a speed value
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The speed value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("SpeedCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("SpeedCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Check if we have a response that includes the mode and PID bytes (41 0D)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "0D") {
            // We have a response with mode and PID included, use the actual data bytes
            val speed = dataBytes[2].hexToInt()
            println("SpeedCommand: Using data bytes after mode+PID, speed: $speed")
            return speed.toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when {
            cleanedResponse.contains("7E80441") -> {
                // Special handling for ELM327 responses
                val dataStart = cleanedResponse.indexOf("7E80441") + 7
                if (dataStart + 2 <= cleanedResponse.length) {
                    val speedValue =
                        cleanedResponse.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                    println("SpeedCommand: ELM327 format - extracted speed: $speedValue")
                    speedValue.toString()
                } else {
                    "0"
                }
            }

            dataBytes.size == 1 -> {
                // Standard single byte format
                val speed = dataBytes[0].hexToInt()
                println("SpeedCommand: Standard format, speed: $speed")
                speed.toString()
            }

            else -> {
                // For responses with more bytes, look for the actual speed data
                // Try to use the last byte
                val speed = dataBytes.last().hexToInt()
                println("SpeedCommand: Using last byte, speed: $speed")
                speed.toString()
            }
        }
    }
}
