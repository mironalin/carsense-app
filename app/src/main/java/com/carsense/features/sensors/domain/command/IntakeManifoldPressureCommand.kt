package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the intake manifold pressure (PID: 0x0B) */
class IntakeManifoldPressureCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.INTAKE_MANIFOLD_PRESSURE
    override val displayName: String = "Intake Manifold Pressure"
    override val displayDescription: String = "Absolute pressure in the intake manifold"
    override val minValue: Float = SensorConstants.Range.PRESSURE_KPA.start
    override val maxValue: Float = SensorConstants.Range.PRESSURE_KPA.endInclusive
    override val unit: String = "kPa"

    /**
     * Parses the raw response string into a pressure value in kPa Formula: A (kPa)
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The pressure value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("IntakeManifoldPressureCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("IntakeManifoldPressureCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Check if we have a response that includes the mode and PID bytes (41 0B)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "0B") {
            // We have a response with mode and PID included, use the actual data bytes
            val pressure = dataBytes[2].hexToInt()
            println(
                    "IntakeManifoldPressureCommand: Using data bytes after mode+PID, pressure: $pressure kPa"
            )
            return pressure.toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when {
            cleanedResponse.contains("7E80440B") -> {
                // Special handling for ELM327 responses
                val dataStart = cleanedResponse.indexOf("7E80440B") + 8
                if (dataStart + 2 <= cleanedResponse.length) {
                    val pressure =
                            cleanedResponse.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                    println(
                            "IntakeManifoldPressureCommand: ELM327 format - pressure: $pressure kPa"
                    )
                    pressure.toString()
                } else {
                    "0"
                }
            }
            dataBytes.size == 1 -> {
                // Standard single byte format: A (kPa)
                val pressure = dataBytes[0].hexToInt()
                println("IntakeManifoldPressureCommand: Standard format, pressure: $pressure kPa")
                pressure.toString()
            }
            else -> {
                // For responses with more bytes, look for the actual pressure data
                // Try to use the last byte
                val pressure = dataBytes.last().hexToInt()
                println("IntakeManifoldPressureCommand: Using last byte, pressure: $pressure kPa")
                pressure.toString()
            }
        }
    }
}
