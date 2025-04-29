package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the mass air flow rate (PID: 0x10, Formula: A * 256 + B / 100 (g/s)) */
class MassAirFlowCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.MAF_RATE
    override val displayName: String = "Mass Air Flow"
    override val displayDescription: String = "Air flow rate from mass air flow sensor"
    override val minValue: Float = 0f
    override val maxValue: Float = 655.35f // Maximum value based on the formula
    override val unit: String = "g/s"

    /**
     * Parses the raw response string into a mass air flow rate in grams/second Formula: (A * 256 +
     * B) / 100 (g/s)
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The MAF rate value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("MassAirFlowCommand: Parsing response: $cleanedResponse")

        // First, try to directly extract MAF data patterns from the raw response
        // Look for the pattern after '410' (mode 1, PID 10) in the response
        if (cleanedResponse.contains("410")) {
            val pattern = "410([0-9A-F]{2})([0-9A-F]{2})".toRegex(RegexOption.IGNORE_CASE)
            val matchResult = pattern.find(cleanedResponse)

            if (matchResult != null && matchResult.groupValues.size >= 3) {
                val a = matchResult.groupValues[1].toIntOrNull(16) ?: 0
                val b = matchResult.groupValues[2].toIntOrNull(16) ?: 0
                val mafRate = (a * 256 + b) / 100.0
                println(
                        "MassAirFlowCommand: Regex matched pattern - A: $a (0x${a.toString(16)}), B: $b (0x${b.toString(16)}), MAF: $mafRate g/s"
                )
                return String.format("%.2f", mafRate)
            }
        }

        // Use the standard extraction method as fallback
        val dataBytes = extractDataBytes(cleanedResponse)
        println("MassAirFlowCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            println("MassAirFlowCommand: No data bytes found, trying direct raw data extraction")
            // Try to directly extract hex pairs from the response
            val hexPairs = cleanedResponse.replace(" ", "").chunked(2)
            println("MassAirFlowCommand: Raw hex pairs: $hexPairs")

            // Try to find where the actual data starts (usually after mode+PID)
            // Special handling for this specific simulator format
            if (hexPairs.size >= 3 && hexPairs.contains("10")) {
                val index = hexPairs.indexOf("10")
                if (index + 2 < hexPairs.size) {
                    val a = hexPairs[index + 1].toIntOrNull(16) ?: 0
                    val b = hexPairs[index + 2].toIntOrNull(16) ?: 0
                    val mafRate = (a * 256 + b) / 100.0
                    println(
                            "MassAirFlowCommand: Raw extraction - A: $a (0x${a.toString(16)}), B: $b (0x${b.toString(16)}), MAF: $mafRate g/s"
                    )
                    return String.format("%.2f", mafRate)
                }
            }

            return "0.00"
        }

        println("MassAirFlowCommand: Bytes in hex: ${dataBytes.joinToString(", ") { "0x$it" }}")

        // Standard OBD-II format handling
        return when {
            // Mode + PID format (41 10 XX XX)
            dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "10" -> {
                val a = dataBytes[2].hexToInt()
                val b = dataBytes[3].hexToInt()
                val mafRate = (a * 256 + b) / 100.0
                println(
                        "MassAirFlowCommand: Mode+PID format - A: $a (0x${a.toString(16)}), B: $b (0x${b.toString(16)}), MAF: $mafRate g/s"
                )
                String.format("%.2f", mafRate)
            }

            // Two data bytes without mode+PID
            dataBytes.size >= 2 -> {
                // Try to find the actual data bytes
                // Some simulators might just return the data bytes
                var a = 0
                var b = 0

                // Look for the case where one byte might be the PID itself
                if (dataBytes.contains("10")) {
                    val index = dataBytes.indexOf("10")
                    if (index + 1 < dataBytes.size) {
                        a = dataBytes[index + 1].hexToInt()
                        if (index + 2 < dataBytes.size) {
                            b = dataBytes[index + 2].hexToInt()
                        }
                    }
                } else {
                    // Otherwise just use the first two bytes
                    a = dataBytes[0].hexToInt()
                    b = dataBytes[1].hexToInt()
                }

                val mafRate = (a * 256 + b) / 100.0
                println(
                        "MassAirFlowCommand: Two byte format - A: $a (0x${a.toString(16)}), B: $b (0x${b.toString(16)}), MAF: $mafRate g/s"
                )
                String.format("%.2f", mafRate)
            }

            // Single data byte - simulator might be sending a compressed value
            dataBytes.size == 1 -> {
                val value = dataBytes[0].hexToInt()

                // The simulator might be sending a compressed or scaled value
                // Try different scale factors to see what makes the most sense
                val directValue = value.toDouble()

                // Scale by 2.55 to convert from 0-255 range to 0-650 range
                // This is a heuristic based on the simulator's max value of ~655
                val scaledValue = value * 2.55
                println(
                        "MassAirFlowCommand: Single byte format - value: $value, scaled: $scaledValue"
                )

                String.format("%.2f", scaledValue)
            }

            // Default case - unrecognized format
            else -> {
                println("MassAirFlowCommand: Unrecognized format")
                // Try to handle non-standard formats
                try {
                    // Consider if all the data bytes together form a single large number
                    val combinedHex = dataBytes.joinToString("")
                    val intValue = combinedHex.toIntOrNull(16)
                    if (intValue != null) {
                        val scale = if (intValue > 6553) 0.01 else if (intValue > 655) 0.1 else 1.0
                        val scaledValue = intValue * scale
                        println(
                                "MassAirFlowCommand: Combined value: $intValue, scaled: $scaledValue"
                        )
                        return String.format("%.2f", scaledValue)
                    }
                } catch (e: Exception) {
                    println("MassAirFlowCommand: Error parsing combined value: ${e.message}")
                }

                "0.00"
            }
        }
    }
}
