package com.carsense.features.obd2.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.obd2.domain.constants.OBD2Constants

/** OBD command for retrieving the engine coolant temperature */
class CoolantTempCommand : OBD2Command() {
    override val mode: Int = OBD2Constants.MODE_CURRENT_DATA
    override val pid: String = OBD2Constants.PID.COOLANT_TEMP
    override val displayName: String = "Coolant Temperature"
    override val displayDescription: String = "Engine coolant temperature"
    override val minValue: Float = -40f
    override val maxValue: Float = 215f
    override val unit: String = "°C"

    /**
     * Parses the raw response string into a temperature value
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The temperature value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("CoolantTempCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("CoolantTempCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Temperature is reported in 1 byte as A - 40
        val tempValue = dataBytes[0].hexToInt() - 40
        println("CoolantTempCommand: Calculated temperature: $tempValue °C")
        return tempValue.toString()
    }
}
