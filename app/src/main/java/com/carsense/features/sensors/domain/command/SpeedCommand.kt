package com.carsense.features.sensors.domain.command

import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the vehicle speed */
class SpeedCommand : SensorCommand() {
    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.VEHICLE_SPEED
    override val displayName: String = "Vehicle Speed"
    override val displayDescription: String = "Current vehicle speed"
    override val minValue: Float = SensorConstants.Range.SPEED.start
    override val maxValue: Float = SensorConstants.Range.SPEED.endInclusive
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

        // Speed is directly expressed in km/h in a single byte
        val speed = dataBytes[0].hexToInt()
        println("SpeedCommand: Speed value: $speed km/h")

        return speed.toString()
    }
}
