package com.carsense.features.obd2.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.obd2.domain.constants.OBD2Constants

/** OBD command for checking which Mode 9 (Vehicle Info) PIDs are supported */
class Mode9SupportCommand : OBD2Command() {
    override val mode: Int = OBD2Constants.MODE_VEHICLE_INFO
    override val pid: String = "00"
    override val displayName: String = "Mode 9 Support"
    override val displayDescription: String = "Supported Mode 9 PIDs (Vehicle Information)"
    override val minValue: Float = 0f
    override val maxValue: Float = 0f
    override val unit: String = ""

    /**
     * Parses the raw response string into a list of supported PIDs
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The list of supported PIDs as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("Mode9SupportCommand: Parsing response: $cleanedResponse")

        try {
            val dataBytes = extractDataBytes(cleanedResponse)
            println("Mode9SupportCommand: Extracted data bytes: $dataBytes")

            if (dataBytes.isEmpty()) {
                println("Mode9SupportCommand: No data bytes found")
                throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
            }

            // We need at least 4 bytes for 32 bits of PID support info
            if (dataBytes.size < 4) {
                println("Mode9SupportCommand: Insufficient data (less than 4 bytes)")
                return "Limited Mode 9 support data"
            }

            // Convert to integers
            val bytes = dataBytes.take(4).map { it.hexToInt() }

            // Calculate supported PIDs (each bit represents a PID)
            val supportedPIDs = mutableListOf<String>()

            for (i in 0 until 32) {
                // Determine which byte and bit to check
                val byteIndex = i / 8
                val bitIndex = 7 - (i % 8) // Most significant bit first

                if (byteIndex < bytes.size) {
                    val isSupported = (bytes[byteIndex] and (1 shl bitIndex)) != 0

                    if (isSupported) {
                        // PIDs start at 01, so add 1 to index
                        val pidNumber = i + 1
                        val pidHex = pidNumber.toString(16).padStart(2, '0').uppercase()
                        supportedPIDs.add(pidHex)
                    }
                }
            }

            // Check specifically for VIN support
            val hasVINSupport = supportedPIDs.contains("02")

            val result = buildString {
                append("Supported PIDs: ${supportedPIDs.joinToString(", ")}")
                append("\nVIN Support: ${if (hasVINSupport) "YES" else "NO"}")
            }

            println("Mode9SupportCommand: Result: $result")
            return result
        } catch (e: Exception) {
            println("Mode9SupportCommand: Error parsing: ${e.message}")
            throw IllegalArgumentException("Failed to parse Mode 9 support: ${e.message}")
        }
    }
}
