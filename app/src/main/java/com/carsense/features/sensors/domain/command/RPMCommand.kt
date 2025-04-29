package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the engine RPM */
class RPMCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.ENGINE_RPM
    override val displayName: String = "Engine RPM"
    override val displayDescription: String = "Current engine revolutions per minute"
    override val minValue: Float = SensorConstants.Range.RPM.start
    override val maxValue: Float = SensorConstants.Range.RPM.endInclusive
    override val unit: String = "rpm"

    /**
     * Parses the raw response string into an RPM value
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The RPM value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("RPMCommand: Parsing response: $cleanedResponse")

        // Special handling for ELM327 formatted responses with 7E8 prefix
        if (cleanedResponse.contains("7E804410C")) {
            return parseELM327Response(cleanedResponse)
        }

        val dataBytes = extractDataBytes(cleanedResponse)
        println("RPMCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Check if we have a response that includes the mode and PID bytes (41 0C)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "0C") {
            // We have a response with mode and PID included, use the actual data bytes
            val a = dataBytes[2].hexToInt()
            val b = dataBytes[3].hexToInt()
            val rpm = (a * 256 + b) / 4.0
            println("RPMCommand: Using data bytes after mode+PID, A: $a, B: $b, RPM: $rpm")
            return rpm.toInt().toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when (dataBytes.size) {
            1 -> {
                // Single byte format (unusual but possible)
                val value = dataBytes[0].hexToInt()
                println("RPMCommand: Single byte format, value: $value")
                value.toString()
            }
            2 -> {
                // Standard 2-byte format: (A * 256 + B) / 4
                val a = dataBytes[0].hexToInt()
                val b = dataBytes[1].hexToInt()
                val rpm = (a * 256 + b) / 4.0
                println("RPMCommand: Standard 2-byte format, A: $a, B: $b, RPM: $rpm")
                rpm.toInt().toString()
            }
            else -> {
                // For responses with more bytes, look for the actual RPM data
                // Try to find the correct position, often the last 2 bytes contain the data
                val a = dataBytes[dataBytes.size - 2].hexToInt()
                val b = dataBytes[dataBytes.size - 1].hexToInt()
                val rpm = (a * 256 + b) / 4.0
                println("RPMCommand: Using last 2 bytes, A: $a, B: $b, RPM: $rpm")
                rpm.toInt().toString()
            }
        }
    }

    /** Parses ELM327 formatted responses with 7E8 header */
    private fun parseELM327Response(response: String): String {
        println("RPMCommand: Parsing ELM327 response: $response")

        try {
            // Find the data bytes after the header and mode+PID
            val dataStart = response.indexOf("7E804410C") + 9
            if (dataStart + 4 <= response.length) {
                val dataByte1 = response.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                val dataByte2 =
                        response.substring(dataStart + 2, dataStart + 4).toIntOrNull(16) ?: 0

                // Apply RPM formula: (A * 256 + B) / 4
                val rpm = (dataByte1 * 256 + dataByte2) / 4
                println(
                        "RPMCommand: ELM327 format - extracted A: $dataByte1, B: $dataByte2, RPM: $rpm"
                )
                return rpm.toString()
            }
        } catch (e: Exception) {
            println("RPMCommand: Error parsing ELM327 response: ${e.message}")
        }

        // Fallback to standard parsing
        return extractDataBytes(response).let { dataBytes ->
            if (dataBytes.size >= 2) {
                val a = dataBytes[dataBytes.size - 2].hexToInt()
                val b = dataBytes[dataBytes.size - 1].hexToInt()
                val rpm = (a * 256 + b) / 4.0
                println("RPMCommand: Fallback parsing, A: $a, B: $b, RPM: $rpm")
                rpm.toInt().toString()
            } else {
                "0"
            }
        }
    }
}
