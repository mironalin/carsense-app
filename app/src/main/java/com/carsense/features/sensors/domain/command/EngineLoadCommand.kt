package com.carsense.features.sensors.domain.command

import android.util.Log
import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/** Command for retrieving the calculated engine load value PID: 0x04 Formula: A * 100 / 255 (%) */
class EngineLoadCommand : SensorCommand() {
    private val TAG = "EngineLoadCommand"

    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = SensorConstants.PID.ENGINE_LOAD
    override val displayName: String = "Engine Load"
    override val displayDescription: String = "Calculated engine load value"
    override val minValue: Float = SensorConstants.Range.PERCENT.start
    override val maxValue: Float = SensorConstants.Range.PERCENT.endInclusive
    override val unit: String = "%"

    /**
     * Parses the raw response string into an engine load percentage
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The engine load as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        Log.d(TAG, "Parsing response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        Log.d(TAG, "Extracted data bytes: $dataBytes")

        if (dataBytes.isEmpty()) {
            throw IllegalArgumentException("No data bytes found in response: $cleanedResponse")
        }

        // Load percentage is expressed as A * 100 / 255
        val loadRaw = dataBytes[0].hexToInt()
        val loadPercent = loadRaw * 100 / 255.0

        Log.d(TAG, "Engine load value: $loadPercent%")
        return loadPercent.toInt().toString()
    }
}
