package com.carsense.features.obd2.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.obd2.domain.constants.OBD2Constants

/** OBD command for retrieving the engine RPM */
class RPMCommand : OBD2Command() {
    override val mode: Int = OBD2Constants.MODE_CURRENT_DATA
    override val pid: String = OBD2Constants.PID.ENGINE_RPM
    override val displayName: String = "Engine RPM"
    override val displayDescription: String = "Current engine revolutions per minute"
    override val minValue: Float = OBD2Constants.Range.RPM.start
    override val maxValue: Float = OBD2Constants.Range.RPM.endInclusive
    override val unit: String = "rpm"

    /**
     * Parses the raw response string into an RPM value
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The RPM value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("RPMCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("RPMCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
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
                // Try to use the first 2 bytes if available
                if (dataBytes.size >= 2) {
                    val a = dataBytes[0].hexToInt()
                    val b = dataBytes[1].hexToInt()
                    val rpm = (a * 256 + b) / 4.0
                    println(
                        "RPMCommand: Using first 2 bytes from multiple, A: $a, B: $b, RPM: $rpm"
                    )
                    rpm.toInt().toString()
                } else {
                    // Last resort: use the first byte as a direct value
                    val value = dataBytes[0].hexToInt()
                    println("RPMCommand: Using first byte as direct value: $value")
                    value.toString()
                }
            }
        }
    }
}
