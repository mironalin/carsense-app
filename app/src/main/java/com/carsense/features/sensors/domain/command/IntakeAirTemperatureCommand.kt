package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the intake air temperature */
class IntakeAirTemperatureCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.INTAKE_AIR_TEMP
    override val displayName: String = "Intake Air Temperature"
    override val displayDescription: String = "Intake air temperature"
    override val minValue: Float = SensorConstants.Range.TEMPERATURE.start
    override val maxValue: Float = SensorConstants.Range.TEMPERATURE.endInclusive
    override val unit: String = "°C"

    /**
     * Parses the raw response string into a temperature value
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The temperature value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("IntakeAirTemperatureCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("IntakeAirTemperatureCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Check if we have a response that includes the mode and PID bytes (41 0F)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "0F") {
            // We have a response with mode and PID included, use the actual data bytes
            val tempRaw = dataBytes[2].hexToInt()
            val tempCelsius = tempRaw - 40
            println(
                    "IntakeAirTemperatureCommand: Using data bytes after mode+PID, Raw: $tempRaw, Celsius: $tempCelsius"
            )
            return tempCelsius.toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when {
            cleanedResponse.contains("7E80440F") -> {
                // Special handling for ELM327 responses
                val dataStart = cleanedResponse.indexOf("7E80440F") + 8
                if (dataStart + 2 <= cleanedResponse.length) {
                    val tempRaw =
                            cleanedResponse.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                    val tempCelsius = tempRaw - 40
                    println(
                            "IntakeAirTemperatureCommand: ELM327 format - Raw: $tempRaw, Celsius: $tempCelsius"
                    )
                    tempCelsius.toString()
                } else {
                    "0"
                }
            }
            dataBytes.size == 1 -> {
                // Standard single byte format: A - 40 (°C)
                val tempRaw = dataBytes[0].hexToInt()
                val tempCelsius = tempRaw - 40
                println(
                        "IntakeAirTemperatureCommand: Standard format, Raw: $tempRaw, Celsius: $tempCelsius"
                )
                tempCelsius.toString()
            }
            else -> {
                // For responses with more bytes, look for the actual temperature data
                // Try to use the last byte
                val tempRaw = dataBytes.last().hexToInt()
                val tempCelsius = tempRaw - 40
                println(
                        "IntakeAirTemperatureCommand: Using last byte, Raw: $tempRaw, Celsius: $tempCelsius"
                )
                tempCelsius.toString()
            }
        }
    }
}
