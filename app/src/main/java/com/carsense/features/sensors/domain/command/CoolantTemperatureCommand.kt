package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the engine coolant temperature */
class CoolantTemperatureCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.COOLANT_TEMP
    override val displayName: String = "Coolant Temperature"
    override val displayDescription: String = "Engine coolant temperature"
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
        println("CoolantTemperatureCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("CoolantTemperatureCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Temperature is expressed as (A - 40) in one byte
        val tempRaw = dataBytes[0].hexToInt()
        val tempCelsius = tempRaw - 40
        println("CoolantTemperatureCommand: Temperature value: $tempCelsius °C")

        return tempCelsius.toString()
    }
}
