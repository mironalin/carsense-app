package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/**
 * Command for retrieving the timing advance (PID: 0x0E, Formula: (A - 128) / 2 (degrees before
 * TDC))
 */
class TimingAdvanceCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.TIMING_ADVANCE
    override val displayName: String = "Timing Advance"
    override val displayDescription: String = "Ignition timing advance for cylinder 1"
    override val minValue: Float = SensorConstants.Range.TIMING_DEGREES.start
    override val maxValue: Float = SensorConstants.Range.TIMING_DEGREES.endInclusive
    override val unit: String = "°"

    /**
     * Parses the raw response string into a timing advance value in degrees Formula: (A - 128) / 2
     * (degrees before TDC)
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The timing advance value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("TimingAdvanceCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("TimingAdvanceCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Check if we have a response that includes the mode and PID bytes (41 0E)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "0E") {
            // We have a response with mode and PID included, use the actual data bytes
            val rawValue = dataBytes[2].hexToInt()
            val timingAdvance = (rawValue - 128) / 2.0
            println(
                "TimingAdvanceCommand: Using data bytes after mode+PID, Raw: $rawValue, Advance: $timingAdvance°"
            )
            return timingAdvance.toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when {
            cleanedResponse.contains("7E80440E") -> {
                // Special handling for ELM327 responses
                val dataStart = cleanedResponse.indexOf("7E80440E") + 8
                if (dataStart + 2 <= cleanedResponse.length) {
                    val rawValue =
                        cleanedResponse.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                    val timingAdvance = (rawValue - 128) / 2.0
                    println(
                        "TimingAdvanceCommand: ELM327 format - Raw: $rawValue, Advance: $timingAdvance°"
                    )
                    timingAdvance.toString()
                } else {
                    "0"
                }
            }

            dataBytes.size == 1 -> {
                // Standard single byte format: (A - 128) / 2 (degrees)
                val rawValue = dataBytes[0].hexToInt()
                val timingAdvance = (rawValue - 128) / 2.0
                println(
                    "TimingAdvanceCommand: Standard format, Raw: $rawValue, Advance: $timingAdvance°"
                )
                timingAdvance.toString()
            }

            else -> {
                // For responses with more bytes, look for the actual timing advance data
                // Try to use the last byte
                val rawValue = dataBytes.last().hexToInt()
                val timingAdvance = (rawValue - 128) / 2.0
                println(
                    "TimingAdvanceCommand: Using last byte, Raw: $rawValue, Advance: $timingAdvance°"
                )
                timingAdvance.toString()
            }
        }
    }
}
