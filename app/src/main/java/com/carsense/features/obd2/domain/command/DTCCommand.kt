package com.carsense.features.obd2.domain.command

import android.util.Log
import com.carsense.core.constants.OBD2Constants
import com.carsense.features.obd2.domain.model.DTCs
import com.carsense.features.obd2.domain.model.ValueResponse

/**
 * Command to read Diagnostic Trouble Codes (DTCs) from the vehicle. Uses Mode 03
 * (MODE_TROUBLE_CODES) which returns stored DTCs.
 */
class DTCCommand : OBD2Command() {
    private val TAG = "DTCCommand"

    override val mode: Int = OBD2Constants.MODE_TROUBLE_CODES
    override val pid: String = "" // Mode 03 doesn't require a PID
    override val displayName: String = "Diagnostic Trouble Codes"
    override val displayDescription: String =
        "Reads stored diagnostic trouble codes from the vehicle"
    override val minValue: Float = 0f
    override val maxValue: Float = 0f // Not applicable for DTCs
    override val unit: String = ""

    override fun getCommand(): String {
        // For DTCs, we just send the mode number
        return "03"
    }

    override fun parseRawValue(rawValue: String): String {
        Log.d(TAG, "Parsing raw DTC response: '$rawValue'")

        if (rawValue.isBlank()) {
            return "NO DATA"
        }

        // Check for error responses
        if (rawValue.contains("UNABLE TO CONNECT", ignoreCase = true) ||
            rawValue.contains("ERROR", ignoreCase = true)
        ) {
            return "Error: Unable to connect to the OBD system"
        }

        if (rawValue.contains("NO DATA", ignoreCase = true) ||
            rawValue.contains("NODATA", ignoreCase = true)
        ) {
            return "NO DATA"
        }

        val result = parseDTCResponse(rawValue.trim())
        return when (result) {
            is ValueResponse.Success -> {
                if (result.data.codes.isEmpty()) {
                    "NO DATA"
                } else {
                    result.data.codes.joinToString(",")
                }
            }

            is ValueResponse.Error -> {
                "Error: ${result.message}"
            }
        }
    }

    /** Main response parser that handles all formats */
    private fun parseDTCResponse(response: String): ValueResponse<DTCs> {
        // Remove all spaces for consistent parsing
        val cleanResponse = response.replace("\\s".toRegex(), "")
        Log.d(TAG, "Clean response: $cleanResponse")

        try {
            // Check if the response contains ELM327 headers
            if (cleanResponse.contains("7E8") || cleanResponse.contains("7E9")) {
                return parseELM327Response(cleanResponse)
            }

            // Check if the response is in standard OBD format (starts with 43)
            if (cleanResponse.startsWith("43")) {
                return parseStandardResponse(cleanResponse)
            }

            // If we can't determine the format, try to extract DTCs directly
            val dtcCodes = extractDTCsDirectly(cleanResponse)
            if (dtcCodes.isNotEmpty()) {
                return ValueResponse.Success(DTCs(dtcCodes))
            }

            return ValueResponse.Success(DTCs(emptyList()))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response", e)
            return ValueResponse.Error("Failed to parse response: ${e.message}")
        }
    }

    /** Parse response in ELM327 format (with 7E8/7E9 headers) */
    private fun parseELM327Response(response: String): ValueResponse<DTCs> {
        Log.d(TAG, "Parsing ELM327 response: $response")

        try {
            // Split the response into frames
            val frames = splitIntoFrames(response)
            Log.d(TAG, "Split into ${frames.size} frames: $frames")

            if (frames.isEmpty()) {
                return ValueResponse.Success(DTCs(emptyList()))
            }

            val dtcs = mutableListOf<String>()

            // Loop through frames to find mode 43 responses
            for (frameIndex in frames.indices) {
                val frame = frames[frameIndex]

                // Find where mode 43 starts in this frame
                val modeIndex = frame.indexOf("43")
                if (modeIndex >= 0) {
                    // Mode 43 found, try to extract DTCs
                    val dtcInfo = extractDTCsFromFrame(frame, modeIndex, frameIndex, frames)
                    dtcs.addAll(dtcInfo)
                }
            }

            Log.d(TAG, "Extracted DTCs: $dtcs")
            return ValueResponse.Success(DTCs(dtcs))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing ELM327 response", e)
            return ValueResponse.Error("Failed to parse ELM327 response: ${e.message}")
        }
    }

    /** Extract DTCs from a frame containing mode 43 response */
    private fun extractDTCsFromFrame(
        frame: String,
        modeIndex: Int,
        frameIndex: Int,
        allFrames: List<String>
    ): List<String> {
        val dtcs = mutableListOf<String>()

        try {
            // Check if we have enough space for the DTC count byte
            if (modeIndex + 4 > frame.length) {
                return emptyList()
            }

            // Extract DTC count (byte after mode 43)
            val countByte = frame.substring(modeIndex + 2, modeIndex + 4)
            val dtcCount = countByte.toIntOrNull(16) ?: 0
            Log.d(TAG, "DTC count: $dtcCount")

            if (dtcCount == 0) {
                return emptyList()
            }

            // Initialize variables for multi-frame processing
            var remainingDtcs = dtcCount
            var currentFrame = frameIndex
            var position = modeIndex + 4 // Start after the mode and count bytes

            while (remainingDtcs > 0 && currentFrame < allFrames.size) {
                val currentFrameData = allFrames[currentFrame]

                while (position + 4 <= currentFrameData.length && remainingDtcs > 0) {
                    // Extract 4 characters (2 bytes) for each DTC
                    val dtcBytes = currentFrameData.substring(position, position + 4)
                    val dtcCode = decodeDTC(dtcBytes)

                    if (dtcCode.isNotEmpty()) {
                        dtcs.add(dtcCode)
                        remainingDtcs--
                    }

                    position += 4
                }

                // Move to next frame if needed
                currentFrame++
                if (currentFrame < allFrames.size) {
                    // For subsequent frames, skip the header bytes (typically 5 chars: 7E8XX)
                    val nextFrame = allFrames[currentFrame]
                    position =
                        if (nextFrame.startsWith("7E8") || nextFrame.startsWith("7E9")) {
                            5
                        } else {
                            0
                        }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting DTCs from frame", e)
        }

        return dtcs
    }

    /** Parse standard OBD response (starting with 43) */
    private fun parseStandardResponse(response: String): ValueResponse<DTCs> {
        try {
            if (response.length < 4) {
                return ValueResponse.Success(DTCs(emptyList()))
            }

            // Extract DTC count (byte after 43)
            val dtcCount = response.substring(2, 4).toIntOrNull(16) ?: 0
            Log.d(TAG, "Standard response DTC count: $dtcCount")

            if (dtcCount == 0) {
                return ValueResponse.Success(DTCs(emptyList()))
            }

            val dtcData = response.substring(4)
            val dtcCodes = mutableListOf<String>()

            var position = 0
            while (position + 4 <= dtcData.length && dtcCodes.size < dtcCount) {
                val dtcBytes = dtcData.substring(position, position + 4)
                val dtcCode = decodeDTC(dtcBytes)

                if (dtcCode.isNotEmpty()) {
                    dtcCodes.add(dtcCode)
                }

                position += 4
            }

            return ValueResponse.Success(DTCs(dtcCodes))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing standard response", e)
            return ValueResponse.Error("Failed to parse standard response: ${e.message}")
        }
    }

    /** Split an ELM327 response into separate frames */
    private fun splitIntoFrames(response: String): List<String> {
        val frames = mutableListOf<String>()
        var index = 0

        while (index < response.length) {
            val hasHeader =
                index + 3 <= response.length &&
                        (response.substring(index, index + 3) == "7E8" ||
                                response.substring(index, index + 3) == "7E9")

            if (hasHeader) {
                // Find the next header
                var nextHeader = response.indexOf("7E8", index + 3)
                if (nextHeader == -1) {
                    nextHeader = response.indexOf("7E9", index + 3)
                }

                if (nextHeader == -1) {
                    // No more headers, take the rest of the string
                    frames.add(response.substring(index))
                    break
                } else {
                    // Add this frame and continue from the next header
                    frames.add(response.substring(index, nextHeader))
                    index = nextHeader
                }
            } else {
                // No header at current position, move ahead
                index++
            }
        }

        return frames
    }

    /** Try to extract DTCs directly from the response string */
    private fun extractDTCsDirectly(response: String): List<String> {
        // Look for formatted DTC codes (P, C, B, U followed by 4 hex digits)
        val dtcPattern = Regex("[PCBU][0-9A-F]{4}")
        val directDtcs = dtcPattern.findAll(response).map { it.value }.toList()

        if (directDtcs.isNotEmpty()) {
            Log.d(TAG, "Found direct DTC codes: $directDtcs")
            return directDtcs
        }

        return emptyList()
    }

    /** Decode a DTC from its hexadecimal string representation */
    private fun decodeDTC(hexString: String): String {
        if (hexString.length != 4) {
            Log.d(TAG, "Invalid DTC hex string length: ${hexString.length}")
            return ""
        }

        try {
            val firstByte = Integer.parseInt(hexString.substring(0, 2), 16)
            val secondByte = Integer.parseInt(hexString.substring(2, 4), 16)

            // Special case for pattern 01 xx (common in ELM327 responses)
            if (firstByte == 0x01) {
                val dtcCode = "P01${secondByte.toString(16).padStart(2, '0')}".uppercase()
                Log.d(TAG, "Decoded 01xx pattern: $hexString as $dtcCode")
                return dtcCode
            }

            // Determine DTC type (first 2 bits of first byte)
            val dtcType =
                when (firstByte shr 6) {
                    0 -> "P" // Powertrain
                    1 -> "C" // Chassis
                    2 -> "B" // Body
                    3 -> "U" // Network
                    else -> "P" // Default to P
                }

            // Skip all-zero DTCs
            if (firstByte == 0 && secondByte == 0) {
                return ""
            }

            // Format the remaining part using masked first byte (bits 5-0) and second byte
            val masked = firstByte.and(0x3F)
            val firstPartHex = masked.toString(16)
            val firstPart = firstPartHex.padStart(1, '0')

            val secondPartHex = secondByte.toString(16)
            val secondPart = secondPartHex.padStart(2, '0')
            val remaining = firstPart + secondPart
            // Format as PXXXX (example: P0123)
            val dtcCode = "${dtcType}0${remaining}".uppercase()
            Log.d(TAG, "Decoded standard format: $hexString as $dtcCode")
            return dtcCode
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding DTC hex string: $hexString", e)
            return ""
        }
    }
}
