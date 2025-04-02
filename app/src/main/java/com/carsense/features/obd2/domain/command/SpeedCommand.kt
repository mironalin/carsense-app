package com.carsense.features.obd2.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.obd2.domain.constants.OBD2Constants

/** OBD command for retrieving the vehicle speed */
class SpeedCommand : OBD2Command() {
    override val mode: Int = OBD2Constants.MODE_CURRENT_DATA
    override val pid: String = OBD2Constants.PID.VEHICLE_SPEED
    override val displayName: String = "Vehicle Speed"
    override val displayDescription: String = "Current vehicle speed in km/h"
    override val minValue: Float = OBD2Constants.Range.SPEED.start
    override val maxValue: Float = OBD2Constants.Range.SPEED.endInclusive
    override val unit: String = "km/h"

    /**
     * Parses the raw response string into a speed value
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The speed value as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("SpeedCommand: Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        println("SpeedCommand: Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Speed is reported in 1 byte (A)
        val speedValue = dataBytes[0].hexToInt()
        println("SpeedCommand: Calculated speed: $speedValue km/h")
        return speedValue.toString()
    }
}
