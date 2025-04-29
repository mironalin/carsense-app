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

        // Check if we have a response that includes the mode and PID bytes (41 04)
        if (dataBytes.size >= 4 && dataBytes[0] == "41" && dataBytes[1] == "04") {
            // We have a response with mode and PID included, use the actual data bytes
            val loadRaw = dataBytes[2].hexToInt()
            val loadPercent = loadRaw * 100 / 255.0
            Log.d(TAG, "Using data bytes after mode+PID, Raw: $loadRaw, Percent: $loadPercent")
            return loadPercent.toInt().toString()
        }

        // Different adapters might return different formats
        // Try to interpret based on the data we have
        return when {
            cleanedResponse.contains("7E804404") -> {
                // Special handling for ELM327 responses
                val dataStart = cleanedResponse.indexOf("7E804404") + 8
                if (dataStart + 2 <= cleanedResponse.length) {
                    val loadRaw =
                            cleanedResponse.substring(dataStart, dataStart + 2).toIntOrNull(16) ?: 0
                    val loadPercent = loadRaw * 100 / 255.0
                    Log.d(TAG, "ELM327 format - Raw: $loadRaw, Percent: $loadPercent")
                    loadPercent.toInt().toString()
                } else {
                    "0"
                }
            }
            dataBytes.size == 1 -> {
                // Standard single byte format: A * 100 / 255 (%)
                val loadRaw = dataBytes[0].hexToInt()
                val loadPercent = loadRaw * 100 / 255.0
                Log.d(TAG, "Standard format, Raw: $loadRaw, Percent: $loadPercent")
                loadPercent.toInt().toString()
            }
            else -> {
                // For responses with more bytes, look for the actual engine load data
                // Try to use the last byte
                val loadRaw = dataBytes.last().hexToInt()
                val loadPercent = loadRaw * 100 / 255.0
                Log.d(TAG, "Using last byte, Raw: $loadRaw, Percent: $loadPercent")
                loadPercent.toInt().toString()
            }
        }
    }
}
