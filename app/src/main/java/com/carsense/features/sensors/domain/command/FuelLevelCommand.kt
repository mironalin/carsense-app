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

        // Fuel level is expressed as A * 100 / 255 (percentage)
        val fuelRaw = dataBytes[0].hexToInt()
        val fuelPercent = fuelRaw * 100 / 255.0
        println("FuelLevelCommand: Fuel level value: $fuelPercent%")

        return fuelPercent.toInt().toString()
    }
}
