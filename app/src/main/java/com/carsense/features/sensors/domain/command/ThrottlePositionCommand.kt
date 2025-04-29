package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the throttle position (PID: 0x11, Formula: A * 100 / 255 (%)) */
class ThrottlePositionCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.THROTTLE_POSITION
    override val displayName: String = "Throttle Position"
    override val displayDescription: String = "Throttle valve position"
    override val minValue: Float = SensorConstants.Range.PERCENT.start
    override val maxValue: Float = SensorConstants.Range.PERCENT.endInclusive
    override val unit: String = "%"

    /**
     * Parses the raw response string into a throttle position percentage
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The throttle position as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("ThrottlePositionCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("ThrottlePositionCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Check if we have a response that includes the mode and PID bytes (41 11)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "11") {
            // We have a response with mode and PID included, use the actual data bytes
            val throttleRaw = dataBytes[2].hexToInt()
            val throttlePercent = throttleRaw * 100 / 255.0
            println(
                "ThrottlePositionCommand: Using data bytes after mode+PID, Raw: $throttleRaw, Percent: $throttlePercent"
            )
            return throttlePercent.toInt().toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when {
            cleanedResponse.contains("7E804411") -> {
                // Special handling for ELM327 responses
                val dataStart = cleanedResponse.indexOf("7E804411") + 8
                if (dataStart + 2 <= cleanedResponse.length) {
                    val throttleRaw =
                        cleanedResponse.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                    val throttlePercent = throttleRaw * 100 / 255.0
                    println(
                        "ThrottlePositionCommand: ELM327 format - Raw: $throttleRaw, Percent: $throttlePercent"
                    )
                    throttlePercent.toInt().toString()
                } else {
                    "0"
                }
            }

            dataBytes.size == 1 -> {
                // Standard single byte format: A * 100 / 255 (%)
                val throttleRaw = dataBytes[0].hexToInt()
                val throttlePercent = throttleRaw * 100 / 255.0
                println(
                    "ThrottlePositionCommand: Standard format, Raw: $throttleRaw, Percent: $throttlePercent"
                )
                throttlePercent.toInt().toString()
            }

            else -> {
                // For responses with more bytes, look for the actual throttle data
                // Try to use the last byte
                val throttleRaw = dataBytes.last().hexToInt()
                val throttlePercent = throttleRaw * 100 / 255.0
                println(
                    "ThrottlePositionCommand: Using last byte, Raw: $throttleRaw, Percent: $throttlePercent"
                )
                throttlePercent.toInt().toString()
            }
        }
    }
}
