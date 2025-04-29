package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the fuel level */
class FuelLevelCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.FUEL_LEVEL
    override val displayName: String = "Fuel Level"
    override val displayDescription: String = "Current fuel tank level"
    override val minValue: Float = SensorConstants.Range.PERCENT.start
    override val maxValue: Float = SensorConstants.Range.PERCENT.endInclusive
    override val unit: String = "%"

    /**
     * Parses the raw response string into a fuel level percentage
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The fuel level as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("FuelLevelCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("FuelLevelCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Check if we have a response that includes the mode and PID bytes (41 2F)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "2F") {
            // We have a response with mode and PID included, use the actual data bytes
            val fuelRaw = dataBytes[2].hexToInt()
            val fuelPercent = fuelRaw * 100 / 255.0
            println(
                    "FuelLevelCommand: Using data bytes after mode+PID, Raw: $fuelRaw, Percent: $fuelPercent"
            )
            return fuelPercent.toInt().toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when {
            cleanedResponse.contains("7E80442F") -> {
                // Special handling for ELM327 responses
                val dataStart = cleanedResponse.indexOf("7E80442F") + 8
                if (dataStart + 2 <= cleanedResponse.length) {
                    val fuelRaw =
                            cleanedResponse.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                    val fuelPercent = fuelRaw * 100 / 255.0
                    println(
                            "FuelLevelCommand: ELM327 format - Raw: $fuelRaw, Percent: $fuelPercent"
                    )
                    fuelPercent.toInt().toString()
                } else {
                    "0"
                }
            }
            dataBytes.size == 1 -> {
                // Standard single byte format: A * 100 / 255 (%)
                val fuelRaw = dataBytes[0].hexToInt()
                val fuelPercent = fuelRaw * 100 / 255.0
                println("FuelLevelCommand: Standard format, Raw: $fuelRaw, Percent: $fuelPercent")
                fuelPercent.toInt().toString()
            }
            else -> {
                // For responses with more bytes, look for the actual fuel level data
                // Try to use the last byte
                val fuelRaw = dataBytes.last().hexToInt()
                val fuelPercent = fuelRaw * 100 / 255.0
                println("FuelLevelCommand: Using last byte, Raw: $fuelRaw, Percent: $fuelPercent")
                fuelPercent.toInt().toString()
            }
        }
    }
}
