package com.carsense.features.obd2.domain.command

import com.carsense.core.command.Command
import com.carsense.core.error.AppError
import com.carsense.core.extensions.buildOBD2Command
import com.carsense.core.extensions.containsOBD2Error
import com.carsense.core.extensions.isAdapterInitializing
import com.carsense.features.obd2.domain.OBD2MessageType
import com.carsense.features.sensors.domain.model.SensorReading

/**
 * Base abstract class for all OBD-II commands.
 *
 * This class defines the common structure and behavior for commands sent to an ELM327 adapter.
 * It implements the [Command] interface, specializing it for [SensorReading] results.
 *
 * Responsibilities include:
 * - Defining properties for OBD mode, PID (Parameter ID), display name, description, expected value range, and unit.
 * - Providing a default implementation for [getCommand] to construct the raw command string (e.g., "010C").
 * - Offering a generic [parseResponse] method that handles common error conditions (empty response,
 *   adapter errors, initialization messages) and delegates specific value parsing to subclasses
 *   via the abstract [parseRawValue] method.
 * - Includes an [extractDataBytes] utility method to help subclasses isolate the relevant data bytes
 *   from potentially complex, header-inclusive OBD2 responses.
 * - Provides a [getFormattedValue] method to attempt parsing and formatting a raw response string
 *   into a human-readable value with units.
 *
 * Subclasses must implement [mode], [pid], [displayName], [displayDescription], [minValue], [maxValue],
 * [unit], and the core logic in [parseRawValue].
 */
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

    /**
     * Returns the raw OBD-II command string to be sent to the adapter.
     *
     * This implementation uses the [buildOBD2Command] extension function, which
     * typically formats the [mode] and [pid] into a standard two-character mode
     * followed by a two-character PID string (e.g., mode 1, PID "0C" becomes "010C").
     * Subclasses can override this if a different command format is required.
     *
     * @return The formatted OBD-II command string.
     */
    override fun getCommand(): String {
        return buildOBD2Command(mode, pid)
    }

    /**
     * Returns the human-readable name of the command.
     *
     * This method is used to get the human-readable name of the command. It is used to get the
     * human-readable name of the command.
     */
    override fun getName(): String {
        return displayName
    }

    /**
     * Returns the description of what the command does.
     *
     * This method is used to get the description of what the command does. It is used to get the
     * description of what the command does.
     */
    override fun getDescription(): String {
        return displayDescription
    }

    /**
     * Parses the raw string response from the OBD-II adapter into a [SensorReading] object.
     *
     * This method first cleans the `rawResponse` using [cleanResponse].
     * It then checks for common conditions:
     * - If the adapter is still initializing (e.g., sending "ELM327" messages).
     * - If the response is blank or contains known OBD-II error strings (e.g., "NO DATA", "ERROR").
     * In these cases, an error [SensorReading] is created using [createErrorReading].
     *
     * Otherwise, it attempts to parse the cleaned response by calling the abstract [parseRawValue]
     * method (which must be implemented by subclasses).
     * - If [parseRawValue] succeeds, a success [SensorReading] is created using [createSuccessReading].
     * - If [parseRawValue] throws an exception (e.g., [NumberFormatException], or custom parsing errors),
     *   an error [SensorReading] is created with the exception message.
     *
     * @param rawResponse The raw string response received from the OBD-II adapter.
     * @return A [SensorReading] object representing either the successfully parsed data or an error state.
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

    /**
     * Cleans and normalizes a raw response string from the OBD-II adapter.
     *
     * This utility method performs common cleanup operations:
     * - Converts the response to uppercase.
     * - Trims leading and trailing whitespace and control characters.
     * - Handles blank input by returning an empty string.
     *
     * Subclasses or the main parsing logic can use this to preprocess responses before
     * attempting to extract data bytes or parse values.
     *
     * @param rawResponse The raw string response from the adapter.
     * @return The cleaned and normalized response string, or an empty string if the input was blank.
     */
    private fun cleanResponse(rawResponse: String): String {
        if (rawResponse.isBlank()) return ""

        // Clean up and normalize the response
        val cleaned = rawResponse.uppercase().trim()

        // Log for debugging
        println("OBD2Command: Cleaning response: $cleaned")

        return cleaned
    }

    /**
     * Extracts the relevant data bytes (as two-character hex strings) from a cleaned OBD-II response.
     *
     * This method attempts to handle various common OBD-II response formats, including:
     * - Responses with a colon separator before the data (e.g., "7E8:410C1234").
     * - Space-separated responses where the mode and PID are present (e.g., "7E8 04 41 0C 12 34").
     * - Compact ISO-TP style responses (e.g., "7E804410C4233") by searching for the
     *   expected mode (e.g., "41" for mode 01) and the command's [pid].
     *
     * If these specific formats are not detected, it falls back to a simpler heuristic of
     * assuming data starts after the first few characters or by splitting by space and
     * taking bytes after the mode and PID.
     *
     * The goal is to isolate the payload bytes (A, B, C, D, etc.) from the full response
     * (e.g., "41 0C AB CD" -> returns `["AB", "CD"]`).
     *
     * Subclasses will use the output of this method in their [parseRawValue] implementation.
     *
     * @param response A cleaned (typically uppercase, trimmed) response string from the OBD-II adapter.
     * @return A list of two-character strings, where each string represents a hexadecimal data byte.
     *         Returns an empty list if no data bytes could be reliably extracted.
     */
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
     * Parses the cleaned OBD-II response string to extract the specific sensor value.
     *
     * This is the core parsing logic that must be implemented by each concrete [OBD2Command] subclass.
     * The implementation should:
     * 1. Typically use [extractDataBytes] to get the payload bytes from the `cleanedResponse`.
     * 2. Convert these hexadecimal byte strings to integers or other appropriate types.
     * 3. Apply the formula specified by the OBD-II standard for the particular PID to calculate
     *    the actual sensor value.
     * 4. Return the calculated value as a String.
     *
     * @param cleanedResponse The cleaned (uppercase, trimmed) response string, from which data needs to be parsed.
     *                        This response is expected to contain the data payload for the command.
     * @return The calculated sensor value as a String.
     * @throws Exception if parsing fails (e.g., [NumberFormatException], incorrect number of data bytes, etc.).
     *                   These exceptions will be caught by [parseResponse] or [getFormattedValue].
     */
    protected abstract fun parseRawValue(cleanedResponse: String): String

    /**
     * Creates a SensorReading representing a successful response.
     *
     * This method is used to create a SensorReading representing a successful response. It is used to
     * create a SensorReading representing a successful response.
     */
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

    /**
     * Creates a SensorReading representing an error.
     *
     * This method is used to create a SensorReading representing an error. It is used to create a
     * SensorReading representing an error.
     */
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

    /**
     * Formats a value with the parameter's unit.
     *
     * This method is used to format a value with the parameter's unit. It is used to format a value
     * with the parameter's unit.
     */
    protected fun formatValue(value: Number): String {
        return "$value $unit"
    }

    /**
     * Parses a raw OBD-II response string and formats the result into a human-readable string,
     * including the unit.
     *
     * This method provides a convenient way to get a displayable string directly from a raw response.
     * It performs the following steps:
     * 1. Checks the `responseType`. If it's [OBD2MessageType.ERROR], it returns a generic error string.
     *    (Note: The `OBD2MessageType` parameter seems to be from a previous design iteration and might not
     *    be fully utilized if `rawResponse` itself already indicates errors like "NO DATA").
     * 2. Cleans the `rawResponse` using [cleanResponse].
     * 3. Checks for adapter initialization messages or common OBD-II error strings in the cleaned response.
     *    If found, returns an appropriate error message (e.g., "Error: Adapter initializing", "Error: No data").
     * 4. Otherwise, it attempts to parse the value by calling the abstract [parseRawValue] method.
     * 5. If [parseRawValue] succeeds, it converts the resulting string value to a Float and then
     *    formats it with the command's [unit] using [formatValue].
     * 6. If [parseRawValue] throws an exception, it catches the exception and returns an error message
     *    string (e.g., "Error: Failed to parse response: ...").
     *
     * @param rawResponse The raw string response received from the OBD-II adapter.
     * @param responseType An optional [OBD2MessageType] indicating the nature of the response.
     *                     Defaults to [OBD2MessageType.RESPONSE]. This parameter's current utility
     *                     might be limited as error states are often also determined from the
     *                     `rawResponse` content itself.
     * @return A string representing the formatted sensor value with its unit (e.g., "1500 RPM", "90.5 °C"),
     *         or an error message if parsing fails or the response indicates an error.
     */
    open fun getFormattedValue(
        rawResponse: String,
        responseType: OBD2MessageType = OBD2MessageType.RESPONSE
    ): String {
        // System.out.println("${this::class.java.simpleName}: Cleaning response: $rawResponse")
        System.out.println(
            "OBD2Command.getFormattedValue: ENTRY for PID ${this.pid}, Raw: $rawResponse, Class: ${this::class.java.name}"
        )

        if (responseType == OBD2MessageType.ERROR) {
            return "Error: ${responseType.name}"
        }

        val cleanedResponse = cleanResponse(rawResponse)

        // Check for adapter initialization state
        if (cleanedResponse.isAdapterInitializing()) {
            return "Error: Adapter initializing"
        }

        return if (cleanedResponse.isBlank() || cleanedResponse.containsOBD2Error()) {
            "Error: No data or error received"
        } else {
            try {
                val value = parseRawValue(cleanedResponse)
                formatValue(value.toFloat())
            } catch (e: Exception) {
                val errorMessage = "Failed to parse response: ${e.message}"
                val appError = AppError.ParseError(errorMessage, e)
                "Error: $errorMessage"
            }
        }
    }
}
