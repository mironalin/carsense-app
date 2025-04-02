package com.carsense.features.obd2.domain.command

import com.carsense.features.obd2.domain.constants.OBD2Constants

/** OBD command for retrieving the Vehicle Identification Number (VIN) */
class VINCommand : OBD2Command() {
    override val mode: Int = OBD2Constants.MODE_VEHICLE_INFO
    override val pid: String = OBD2Constants.PID.VIN
    override val displayName: String = "VIN"
    override val displayDescription: String = "Vehicle Identification Number"
    override val minValue: Float = 0f
    override val maxValue: Float = 0f
    override val unit: String = ""

    /**
     * Parses the raw response string into a VIN string
     *
     * @param cleanedResponse The cleaned OBD-II response
     * @return The VIN as a string
     */
    override fun parseRawValue(cleanedResponse: String): String {
        println("VINCommand: Parsing response: $cleanedResponse")

        // VIN can come in multiple formats due to multiline responses
        try {
            // Check if we have a response with FF (not supported)
            if (cleanedResponse.contains("FF") &&
                cleanedResponse.count { it == 'F' } > cleanedResponse.length / 3
            ) {
                println("VINCommand: Response contains many FF bytes, VIN likely not supported")
                throw IllegalArgumentException(
                    "VIN not supported by vehicle (response contains mostly FF)"
                )
            }

            // Split into lines and process
            val lines = cleanedResponse.split("\r", "\n", ">").filter { it.isNotEmpty() }
            if (lines.isEmpty()) {
                throw IllegalArgumentException("Empty response")
            }

            println("VINCommand: Processing ${lines.size} lines")

            // Detect if we have a CAN format with header
            val isCAN = cleanedResponse.contains(":")

            val vinBytes =
                if (isCAN) {
                    parseCANFormat(lines)
                } else {
                    parseStandardFormat(lines)
                }

            // Convert bytes to ASCII characters
            val vin = vinBytes.map { it.toChar() }.joinToString("")

            println("VINCommand: Final VIN: $vin")

            // Validate VIN
            if (vin.length < 5 || !isValidVIN(vin)) {
                throw IllegalArgumentException("Invalid VIN format: $vin")
            }

            return vin
        } catch (e: Exception) {
            println("VINCommand: Error parsing VIN: ${e.message}")
            throw IllegalArgumentException("VIN not supported: ${e.message}")
        }
    }

    /** Parse CAN format VIN response (with line numbers like "0: 49 02 01...") */
    private fun parseCANFormat(lines: List<String>): List<Int> {
        val dataBytes = mutableListOf<Int>()

        // Check if first line contains byte count (e.g., "014")
        var startLine = 0
        if (lines[0].trim().matches(Regex("\\d+"))) {
            println("VINCommand: CAN format with byte count: ${lines[0]}")
            startLine = 1
        }

        // Process each line
        for (i in startLine until lines.size) {
            val line = lines[i].trim()
            println("VINCommand: Processing CAN line: $line")

            // Extract sequence number and data
            val parts = line.split(":")
            if (parts.size >= 2) {
                val data = parts[1].trim()
                val bytes = parseHexBytes(data)

                // For first frame, skip the first 3 bytes (49 02 01)
                if (i == startLine && bytes.size >= 3) {
                    dataBytes.addAll(bytes.subList(3, bytes.size))
                } else {
                    dataBytes.addAll(bytes)
                }
            }
        }

        println("VINCommand: Extracted ${dataBytes.size} bytes from CAN format")
        return dataBytes
    }

    /** Parse standard (J1850) format VIN response Example: "49 02 01 00 00 00 31" */
    private fun parseStandardFormat(lines: List<String>): List<Int> {
        val frameData = mutableMapOf<Int, List<Int>>()

        // Process each line
        for (line in lines) {
            val trimmed = line.trim()
            println("VINCommand: Processing standard line: $trimmed")

            if (trimmed.contains("49") && trimmed.contains("02")) {
                val bytes = parseHexBytes(trimmed)
                if (bytes.size >= 3) {
                    // Extract frame number (third byte)
                    val frameNumber = bytes[2]
                    // Extract data (remaining bytes)
                    val data = bytes.subList(3, bytes.size)
                    frameData[frameNumber] = data
                    println("VINCommand: Frame $frameNumber with ${data.size} bytes")
                }
            }
        }

        // Combine frames in order
        val allBytes = mutableListOf<Int>()

        // Sort by frame number
        val sortedFrames = frameData.keys.sorted()
        for (frameNum in sortedFrames) {
            allBytes.addAll(frameData[frameNum] ?: emptyList())
        }

        // Filter out filler bytes (zeros at the beginning)
        val filtered = allBytes.dropWhile { it == 0 }

        println("VINCommand: Extracted ${filtered.size} bytes from standard format")
        return filtered
    }

    /** Parse a string of hex values into a list of integers */
    private fun parseHexBytes(hexString: String): List<Int> {
        // Handle space-separated or continuous hex
        val hexValues =
            if (hexString.contains(" ")) {
                hexString.split(" ")
            } else {
                hexString.chunked(2)
            }

        return hexValues.mapNotNull { hex ->
            try {
                if (hex.isEmpty() || !hex.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
                ) {
                    null
                } else {
                    hex.toInt(16)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Validate if a string is a valid VIN VINs consist of 17 alphanumeric characters (no I, O, Q)
     */
    private fun isValidVIN(vin: String): Boolean {
        // Basic validation - length and allowed characters
        if (vin.length != 17) return false

        // Check for valid characters (no I,O,Q)
        val invalidChars = setOf('I', 'O', 'Q')
        return vin.all { it.isLetterOrDigit() && it !in invalidChars }
    }
}
