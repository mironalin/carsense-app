package com.carsense.features.sensors.domain.command

import android.util.Log
import com.carsense.core.extensions.hexToInt
import com.carsense.features.sensors.domain.constants.SensorConstants

/**
 * Command to retrieve the supported PIDs (Parameter IDs) from the vehicle. Uses Mode 01 PID 00 to
 * request PIDs supported in the range 01 to 20.
 */
class SupportedPIDsCommand : SensorCommand() {
    private val TAG = "SupportedPIDsCommand"

    override val mode: Int = SensorConstants.MODE_CURRENT_DATA
    override val pid: String = "00" // PID 00 returns supported PIDs 01-20
    override val displayName: String = "Supported PIDs"
    override val displayDescription: String = "PIDs supported by the vehicle"
    override val minValue: Float = 0f
    override val maxValue: Float = 0f // Not applicable
    override val unit: String = ""

    /**
     * Parses the raw response and extracts the supported PIDs The response is a 4-byte bitmask
     * where each bit represents whether a PID is supported
     */
    override fun parseRawValue(cleanedResponse: String): String {
        Log.d(TAG, "Parsing supported PIDs response: $cleanedResponse")

        val dataBytes = extractDataBytes(cleanedResponse)
        Log.d(TAG, "Extracted data bytes: $dataBytes")

        if (dataBytes.size < 4) {
            Log.e(TAG, "Not enough data bytes to determine supported PIDs")
            return ""
        }

        try {
            // Convert the 4 data bytes to integers
            val a = dataBytes[0].hexToInt()
            val b = dataBytes[1].hexToInt()
            val c = dataBytes[2].hexToInt()
            val d = dataBytes[3].hexToInt()

            // Create a combined bit mask
            val bitmask =
                (a.toLong() shl 24) or (b.toLong() shl 16) or (c.toLong() shl 8) or d.toLong()

            // Determine which PIDs are supported
            val supportedPIDs = mutableListOf<String>()
            for (i in 1..32) {
                val mask = 0x80000000 ushr (i - 1)
                if ((bitmask and mask) != 0L) {
                    // This PID is supported
                    val pidNumber = String.format("%02X", i)
                    supportedPIDs.add(pidNumber)
                }
            }

            Log.d(TAG, "Supported PIDs: $supportedPIDs")
            return supportedPIDs.joinToString(",")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing supported PIDs", e)
            return ""
        }
    }
}
