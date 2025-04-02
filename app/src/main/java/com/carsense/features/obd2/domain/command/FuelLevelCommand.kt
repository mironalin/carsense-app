package com.carsense.features.obd2.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.obd2.domain.constants.OBD2Constants

/** OBD command for retrieving the fuel tank level */
class FuelLevelCommand : OBD2Command() {
    override val mode: Int = OBD2Constants.MODE_CURRENT_DATA
    override val pid: String = OBD2Constants.PID.FUEL_LEVEL
    override val displayName: String = "Fuel Level"
    override val displayDescription: String = "Fuel tank level as percentage"
    override val minValue: Float = 0f
    override val maxValue: Float = 100f
    override val unit: String = "%"

    /**
     * Parses the raw response string into a fuel level percentage
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The fuel level percentage as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("FuelLevelCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("FuelLevelCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Fuel level is reported in 1 byte as A * 100 / 255
        val rawValue = dataBytes[0].hexToInt()
        val fuelPercentage = (rawValue * 100.0 / 255.0)
        println("FuelLevelCommand: Calculated fuel level: $fuelPercentage%")

        // Format to one decimal place
        return String.format("%.1f", fuelPercentage)
    }
}
