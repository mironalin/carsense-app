package com.carsense.features.obd2.domain.command

import com.carsense.core.command.Command
import com.carsense.core.error.AppError
import com.carsense.core.extensions.buildOBD2Command
import com.carsense.core.extensions.containsOBD2Error
import com.carsense.core.extensions.isAdapterInitializing
import com.carsense.features.sensors.domain.model.SensorReading

/** Base class for all OBD2 commands */
abstract class OBD2Command : Command<SensorReading> {
    /** The OBD mode (service) number */
    abstract val mode: Int

    /** The Parameter ID (PID) */
    abstract val pid: String

    /** Human-readable name of the parameter */
    abstract val displayName: String

    /** Detailed description of the parameter */
    abstract val displayDescription: String

    /** Minimum expected value */
    abstract val minValue: Float

    /** Maximum expected value */
    abstract val maxValue: Float

    /** Unit of measurement (e.g., "km/h", "°C") */
    abstract val unit: String

    /** Returns the raw string that will be sent to the OBD adapter */
    override fun getCommand(): String {
        return buildOBD2Command(mode, pid)
    }

    /** Human-readable name of the command */
    override fun getName(): String {
        return displayName
    }

    /** Description of what the command does */
    override fun getDescription(): String {
        return displayDescription
    }

    /**
     * Parse the raw response from the OBD adapter
     *
     * @param rawResponse The string response from the adapter
     * @return The parsed SensorReading
     */
    override fun parseResponse(rawResponse: String): SensorReading {
        val cleanedResponse = cleanResponse(rawResponse)

        // Check for adapter initialization state
        if (cleanedResponse.isAdapterInitializing()) {
            return createErrorReading("Adapter initializing")
        }

        return if (cleanedResponse.isBlank() || cleanedResponse.containsOBD2Error()) {
            createErrorReading("No data or error received")
        } else {
            try {
                val value = parseRawValue(cleanedResponse)
                createSuccessReading(value, rawResponse)
            } catch (e: Exception) {
                val errorMessage = "Failed to parse response: ${e.message}"
                val appError = AppError.ParseError(errorMessage, e)
                createErrorReading(errorMessage)
            }
        }
    }

    /** Cleans up a raw response from the OBD-II adapter */
    private fun cleanResponse(rawResponse: String): String {
        if (rawResponse.isBlank()) return ""

        // Clean up and normalize the response
        val cleaned = rawResponse.uppercase().trim()

        // Log for debugging
        println("OBD2Command: Cleaning response: $cleaned")

        return cleaned
    }

    /** Extract data bytes from a response (to be implemented by the specific command) */
    protected fun extractDataBytes(response: String): List<String> {
        println("OBD2Command: Extracting data bytes from: $response")

        // Check if we have a response with headers (format with : or with header bytes)
        if (response.contains(":")) {
            // Format with headers like "7E8:410C1234"
            val dataPart = response.substringAfter(":")
            println("OBD2Command: Found header format with ':', data part: $dataPart")

            // Extract bytes after mode and PID
            if (dataPart.length >= 4) {
                val dataBytes = dataPart.substring(4).chunked(2)
                println("OBD2Command: Extracted data bytes: $dataBytes")
                return dataBytes
            }
        }
        // Check for format like "7E8 04 41 0C 12 34"
        else if (response.length >= 12 && response.contains(" ")) {
            val parts = response.split(" ")
            // Find the mode response (should be 41, 42, 43, etc.)
            val modeIndex =
                parts.indexOfFirst {
                    it == "41" ||
                            it == "42" ||
                            it == "43" ||
                            it == "44" ||
                            it == "45" ||
                            it == "46" ||
                            it == "47" ||
                            it == "48" ||
                            it == "49"
                }

            if (modeIndex >= 0 && modeIndex + 2 < parts.size) {
                val dataBytes = parts.subList(modeIndex + 2, parts.size)
                println("OBD2Command: Extracted data bytes from space-separated format: $dataBytes")
                return dataBytes
            }
        }
        // Complex format like "7E804410C4233" (header + length + mode + pid + data)
        else if (response.length >= 6) {
            // Try to find mode and PID location
            val expectedMode = (mode + 0x40).toString(16).padStart(2, '0').uppercase()
            val expectedPid = pid.uppercase()

            println(
                "OBD2Command: Looking for mode $expectedMode and PID $expectedPid in: $response"
            )

            // Find where the mode+PID starts
            val modeIndex = response.indexOf(expectedMode + expectedPid)
            if (modeIndex >= 0) {
                // Extract data after the mode+PID
                val dataStart = modeIndex + expectedMode.length + expectedPid.length
                if (dataStart < response.length) {
                    val dataBytes = response.substring(dataStart).chunked(2)
                    println(
                        "OBD2Command: Found mode+PID at index $modeIndex, extracted data: $dataBytes"
                    )
                    return dataBytes
                }
            } else {
                // Last resort: assume a standard format after the first 6 characters
                // This is a heuristic approach that assumes the first bytes are header/length
                val dataBytes = response.substring(6).chunked(2)
                println("OBD2Command: Using fallback extraction, data: $dataBytes")
                return dataBytes
            }
        }

        // Original implementation as fallback
        val parts = response.split(" ")
        // Skip mode and PID bytes (first two bytes)
        val result = if (parts.size > 2) parts.subList(2, parts.size) else emptyList()
        println("OBD2Command: Fallback extraction result: $result")
        return result
    }

    /**
     * Parses the raw response string into a value This must be implemented by concrete command
     * classes
     */
    protected abstract fun parseRawValue(cleanedResponse: String): String

    /** Creates a SensorReading representing a successful response */
    protected fun createSuccessReading(value: String, rawValue: String): SensorReading {
        return SensorReading(
            name = displayName,
            value = value,
            unit = unit,
            pid = pid,
            mode = mode,
            timestamp = System.currentTimeMillis(),
            rawValue = rawValue,
            isError = false
        )
    }

    /** Creates a SensorReading representing an error */
    protected fun createErrorReading(errorMessage: String): SensorReading {
        return SensorReading(
            name = displayName,
            value = errorMessage,
            unit = "",
            pid = pid,
            mode = mode,
            timestamp = System.currentTimeMillis(),
            rawValue = "",
            isError = true
        )
    }

    /** Formats a value with the parameter's unit */
    protected fun formatValue(value: Number): String {
        return "$value $unit"
    }
}
