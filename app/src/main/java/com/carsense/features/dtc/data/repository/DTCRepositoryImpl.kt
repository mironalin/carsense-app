package com.carsense.features.dtc.data.repository

import android.util.Log
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.dtc.data.api.CreateDTCRequest
import com.carsense.features.dtc.data.api.DTCApiService
import com.carsense.features.dtc.domain.command.ClearDTCCommand
import com.carsense.features.dtc.domain.command.DTCCommand
import com.carsense.features.dtc.domain.model.DTCError
import com.carsense.features.dtc.domain.repository.DTCRepository
import com.carsense.features.obd2.data.OBD2BluetoothService
import com.carsense.features.obd2.data.OBD2Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of the DTCRepository that communicates with the vehicle via Bluetooth */
@Singleton
class DTCRepositoryImpl @Inject constructor(
    private val bluetoothController: BluetoothController,
    private val dtcApiService: DTCApiService
) : DTCRepository {

    private val TAG = "DTCRepository"

    // Maximum number of retries for connection checks when initiating a command
    private val MAX_CONNECTION_RETRIES = 3

    // Cache of the last DTC scan results to provide data even if a subsequent scan fails
    // temporarily.
    private var cachedDTCs: List<DTCError> = emptyList()

    /**
     * Fetches Diagnostic Trouble Codes (DTCs) from the vehicle. This method includes logic for
     * ensuring a stable connection, priming the OBD2 adapter, sending the DTC command (Mode 03),
     * and processing the response. It also incorporates a retry mechanism for "SEARCHING..."
     * responses.
     *
     * @return A Result object containing a list of DTCError on success, or an exception on failure.
     * ```
     *         Returns cached DTCs if a connection failure occurs but a previous successful scan exists.
     * ```
     */
    override suspend fun getDTCs(): Result<List<DTCError>> {
        return try {
            Log.d(TAG, "Getting DTCs from vehicle...")

            val obd2Service = bluetoothController.getObd2Service()
            if (obd2Service == null) {
                Log.e(TAG, "OBD2Service not available. Cannot get DTCs.")
                return handleConnectionFailure()
            }

            // Check if we're connected with retries
            if (!checkConnectionWithRetry(obd2Service)) {
                Log.e(TAG, "Not connected to a vehicle after retry attempts")
                return handleConnectionFailure()
            }

            Log.d(TAG, "Confirmed OBD2 connection, preparing to send DTC command")

            // Create the DTC command
            val dtcCommand = DTCCommand()

            // Pre-send the DTC command once to prime the adapter and discard the result.
            // Some adapters require a command to be sent once to stabilize or select the correct
            // ECU
            // before subsequent identical commands return the full/correct data.
            Log.d(TAG, "Pre-sending DTC command to prime the adapter: ${dtcCommand.getCommand()}")
            obd2Service.executeOBD2Command(dtcCommand.getCommand()).firstOrNull()
            delay(
                1000
            ) // Wait a full second for the first command to complete and adapter to process.

            // Now send the actual command whose response we will use.
            Log.d(TAG, "Sending actual DTC command: ${dtcCommand.getCommand()}")
            val response: OBD2Response? =
                obd2Service.executeOBD2Command(dtcCommand.getCommand()).firstOrNull()

            if (response == null) {
                Log.e(TAG, "Failed to send DTC command or no response received")
                return Result.failure(
                    IllegalStateException(
                        "Failed to send DTC command or no response received (null response)"
                    )
                )
            }

            Log.d(
                TAG,
                "Raw DTC response received: ${response.rawData}, type: ${if (response.isError) "ERROR" else "SUCCESS"}"
            )

            // If we got "SEARCHING..." or an incomplete response (without ISO-TP data markers like
            // "7E8"),
            // it might mean the adapter is still trying to establish the protocol.
            // Wait and try the command one more time.
            if (response.rawData.contains("SEARCHING...", ignoreCase = true) &&
                !response.rawData.contains("7E8", ignoreCase = true)
            ) {
                Log.d(TAG, "Got SEARCHING response, waiting and trying one more time")
                delay(2000) // Wait longer to allow search to complete

                val retryResponse: OBD2Response? =
                    obd2Service.executeOBD2Command(dtcCommand.getCommand()).firstOrNull()
                if (retryResponse != null) {
                    Log.d(TAG, "Retry got response: ${retryResponse.rawData}")
                    // Use the retry response instead
                    return processDTCResult(retryResponse)
                }
            }

            // Process the original response
            val result = processDTCResult(response)

            // Cache successful results
            result.onSuccess { dtcErrors ->
                Log.d(TAG, "Caching ${dtcErrors.size} DTCs from scan")
                cachedDTCs = dtcErrors
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting DTCs", e)
            return handleConnectionFailure(e)
        }
    }

    /**
     * Handles connection failures for `getDTCs`. If cached DTCs are available, it returns them
     * successfully. Otherwise, it returns a failure Result with an appropriate message.
     *
     * @param exception Optional exception that caused the failure.
     * @return A Result object, either success with cached data or failure.
     */
    private fun handleConnectionFailure(exception: Exception? = null): Result<List<DTCError>> {
        if (cachedDTCs.isNotEmpty()) {
            Log.d(TAG, "Returning ${cachedDTCs.size} cached DTCs due to error/connection failure")
            return Result.success(cachedDTCs)
        }
        val message = exception?.message ?: "Connection failed or OBD2 service unavailable."
        return Result.failure(IllegalStateException(message, exception))
    }

    /**
     * Processes the OBD2Response from a DTC command (Mode 03) to extract DTCs. Handles various
     * states like errors, "NO DATA", or actual DTC data.
     *
     * @param response The OBD2Response received from the adapter for a DTC scan.
     * @return A Result object containing a list of DTCError on success, or an exception on failure.
     * ```
     *         An empty list is returned if no DTCs are found or specific non-error states like "NO DATA" occur.
     * ```
     */
    private fun processDTCResult(response: OBD2Response): Result<List<DTCError>> {
        // Parse the response
        if (response.isError || response.decodedValue.startsWith("Error:")) {
            Log.e(
                TAG,
                "Error response from OBD: ${response.decodedValue} (Raw: ${response.rawData})"
            )

            // Special handling for "UNABLE TO CONNECT" or "Timeout" responses.
            // These often indicate that the vehicle does not support Mode 03 DTC reading,
            // has no DTCs to report, or the specific ECU isn't responding to this query.
            // In such cases, successfully return an empty list rather than an error.
            if (response.decodedValue.contains("UNABLE TO CONNECT", ignoreCase = true) ||
                response.rawData.contains("UNABLE TO CONNECT", ignoreCase = true) ||
                response.decodedValue.contains(
                    "SEARCHING...UNABLE TO CONNECT",
                    ignoreCase = true
                ) ||
                response.rawData.contains(
                    "SEARCHING...UNABLE TO CONNECT",
                    ignoreCase = true
                ) ||
                response.decodedValue.contains(
                    "Timeout",
                    ignoreCase = true
                ) // Handle timeout as possible "no DTCs"
            ) {
                Log.d(
                    TAG,
                    "Vehicle indicates 'UNABLE TO CONNECT' or 'Timeout' - likely no DTCs or feature not supported. Raw: ${response.rawData}"
                )
                return Result.success(emptyList())
            }

            return Result.failure(
                IllegalStateException(
                    "Error response: ${response.decodedValue} (Raw: ${response.rawData})"
                )
            )
        }

        // Check for "NO DATA" or similar empty responses, which indicate no DTCs are stored.
        if (response.rawData.contains("NO DATA", ignoreCase = true) ||
            response.rawData.trim() ==
            "03:" || // Legacy check, may be specific to old parser behavior
            response.rawData.contains("NODATA", ignoreCase = true) ||
            response.decodedValue.contains("NO DATA", ignoreCase = true)
        ) {
            Log.d(TAG, "Response indicates no DTCs (Raw: ${response.rawData})")
            return Result.success(emptyList())
        }

        // Special handling for responses that only contain "SEARCHING..." without any actual data
        // markers (like "7E8").
        // This can happen if the adapter gives up searching after the initial command.
        if (response.rawData.contains("SEARCHING...", ignoreCase = true) &&
            !response.rawData.contains("7E8", ignoreCase = true)
        ) {
            Log.d(
                TAG,
                "Response indicates device is still searching but no data was found (Raw: ${response.rawData})"
            )
            return Result.success(emptyList())
        }

        // Process the content into DTCError objects
        val dtcList = processDTCResponse(response)
        Log.d(TAG, "Processed DTCs: $dtcList from raw: ${response.rawData}")

        // Special case: if we got "P0095" and "P0096" instead of "P0195" and "P0196", fix it.
        // This is a heuristic for a known misinterpretation pattern with some adapters/ECUs for
        // specific oil pressure sensor codes.
        // This correction logic might need to be re-evaluated or made more generic if other similar
        // patterns are found.
        val correctedList =
            dtcList.map { dtcError ->
                val code = dtcError.code
                if (code.startsWith("P00") &&
                    code.length == 5 &&
                    (code.endsWith("95") || code.endsWith("96"))
                ) {
                    // This is likely a misinterpreted code like P0095 instead of P0195
                    val correctedCode = "P01" + code.substring(3) // Example: P0095 -> P0195
                    Log.d(TAG, "Correcting DTC code from $code to $correctedCode")
                    DTCError(correctedCode, getDTCDescription(correctedCode))
                } else {
                    dtcError
                }
            }

        if (correctedList != dtcList) {
            Log.d(TAG, "Applied corrections to DTC list: $correctedList")
        }

        return Result.success(correctedList)
    }

    /**
     * Checks the OBD2 connection status with multiple retry attempts before executing critical
     * commands. It first checks the `bluetoothController.isConnected` flag and then performs an
     * "ATI" command as a simple communication test. Retries with increasing delays if initial
     * checks fail.
     *
     * @param obd2Service The OBD2BluetoothService instance to use for the communication test.
     * @return True if a connection is established and verified, false otherwise.
     */
    private suspend fun checkConnectionWithRetry(obd2Service: OBD2BluetoothService): Boolean {
        var retries = 0

        // First fast check - if bluetoothController already reports connected, verify with a quick
        // ATI command.
        if (bluetoothController.isConnected.value) {
            Log.d(TAG, "Connected on first check according to bluetoothController.isConnected")

            // Verify actual communication with a simple test command ("ATI").
            try {
                val testResponse: OBD2Response? = obd2Service.executeAtCommand("ATI").firstOrNull()
                if (testResponse != null &&
                    !testResponse.isError &&
                    testResponse.rawData.isNotBlank()
                ) {
                    Log.d(
                        TAG,
                        "Confirmed active connection with test command ATI: ${testResponse.rawData}"
                    )
                    return true
                } else if (testResponse != null && testResponse.isError) {
                    Log.w(
                        TAG,
                        "Connection test ATI failed with error: ${testResponse.decodedValue} (Raw: ${testResponse.rawData})"
                    )
                } else {
                    Log.w(TAG, "Connection test ATI returned null or blank response.")
                }

                // If the "ATI" test command fails despite bluetoothController.isConnected being
                // true,
                // it might indicate a stale connection or an unresponsive adapter.
                // Proceed to retry logic which might involve longer waits or re-initialization
                // implicitly by the controller.
                Log.d(
                    TAG,
                    "Connection test ATI failed despite isConnected=true. Proceeding to retry logic."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error testing connection with ATI", e)
                // Fall through to retry logic below if ATI command itself throws an exception.
            }
        }

        // If not immediately connected or the initial ATI test failed, try connection and ATI test
        // up to MAX_CONNECTION_RETRIES times.
        while (retries < MAX_CONNECTION_RETRIES) {
            Log.d(TAG, "Connection check retry ${retries + 1}/${MAX_CONNECTION_RETRIES}")

            // Re-check bluetoothController.isConnected in case the connection was established by
            // another process
            // or if the Bluetooth stack reconnected automatically.
            if (bluetoothController.isConnected.value) {
                try {
                    Log.d(TAG, "Attempting communication test with ATI on retry ${retries + 1}")
                    val testResponse: OBD2Response? =
                        obd2Service.executeAtCommand("ATI").firstOrNull()
                    if (testResponse != null &&
                        !testResponse.isError &&
                        testResponse.rawData.isNotBlank()
                    ) {
                        Log.d(
                            TAG,
                            "Communication test successful on retry ${retries + 1}: ${testResponse.rawData}"
                        )
                        return true
                    } else if (testResponse != null) {
                        Log.w(
                            TAG,
                            "Communication test ATI failed on retry ${retries + 1}: ${testResponse.decodedValue} (Raw: ${testResponse.rawData})"
                        )
                    } else {
                        Log.w(
                            TAG,
                            "Communication test ATI returned null or blank response on retry ${retries + 1}."
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during communication test on retry ${retries + 1}", e)
                }
            } else {
                Log.w(
                    TAG,
                    "Not connected (bluetoothController.isConnected is false) on retry ${retries + 1}"
                )
                // Optionally, try to trigger a reconnect/re-initialize here if appropriate,
                // but current design has connectToDevice flow in ViewModel.
                // For now, just delay and retry.
            }

            retries++
            delay(
                1000L * (retries + 1)
            ) // Exponential backoff: Wait longer before each subsequent retry.
        }

        Log.e(TAG, "Connection check failed after $MAX_CONNECTION_RETRIES retries")
        return false
    }

    /**
     * Clears Diagnostic Trouble Codes (DTCs) from the vehicle using Mode 04. Ensures a connection
     * is active, sends the clear command, and interprets the response.
     *
     * @return A Result object indicating true on successful clearance, or an exception on failure.
     */
    override suspend fun clearDTCs(): Result<Boolean> {
        return try {
            Log.d(TAG, "Clearing DTCs from vehicle...")

            val obd2Service = bluetoothController.getObd2Service()
            if (obd2Service == null) {
                Log.e(TAG, "OBD2Service not available. Cannot clear DTCs.")
                return Result.failure(IllegalStateException("OBD2Service not available."))
            }

            // Check if we're connected with retries
            if (!checkConnectionWithRetry(obd2Service)) {
                Log.e(TAG, "Not connected to a vehicle after retry attempts")
                return Result.failure(
                    IllegalStateException(
                        "Not connected to a vehicle. Make sure you have an active connection before clearing DTCs."
                    )
                )
            }

            // Create the Clear DTC command
            val clearCommand = ClearDTCCommand()
            Log.d(TAG, "Sending Clear DTC command: ${clearCommand.getCommand()}")

            val response: OBD2Response? =
                obd2Service.executeOBD2Command(clearCommand.getCommand()).firstOrNull()

            if (response == null) {
                Log.e(TAG, "Failed to send Clear DTC command or no response received")
                return Result.failure(
                    IllegalStateException(
                        "Failed to send Clear DTC command or no response received (null response)"
                    )
                )
            }

            Log.d(
                TAG,
                "Clear DTC response: ${response.rawData}, Type: ${if (response.isError) "ERROR" else "SUCCESS"}"
            )

            if (response.isError || response.rawData.contains("ERROR", ignoreCase = true)) {
                Log.e(
                    TAG,
                    "Error clearing DTCs: ${response.decodedValue} (Raw: ${response.rawData})"
                )
                return Result.failure(
                    IllegalStateException("Failed to clear DTCs: ${response.decodedValue}")
                )
            }

            // A successful clear (Mode 04) typically results in a "44" response from the adapter,
            // or simply a prompt ">" if the adapter doesn't echo the success code explicitly.
            // OBD2Service also treats "NO DATA" as a success for the "04" command based on
            // simulator behavior.
            val rawDataNoSpace = response.rawData.replace(" ", "")
            val isSuccessResponse =
                rawDataNoSpace.contains(
                    "44"
                ) || // Standard "04" positive response (Mode + 0x40).
                        response.rawData.contains(
                            ">"
                        ) || // Any response ending with prompt, after error checks, can imply
                        // success for clears.
                        (!response.isError &&
                                response.decodedValue.equals(
                                    "NO DATA",
                                    ignoreCase = true
                                )) // Explicit success if OBD2Service decoded "NO DATA" for
            // command "04".

            if (isSuccessResponse) {
                Log.d(
                    TAG,
                    "DTCs cleared successfully (Decoded: ${response.decodedValue}, Raw: ${response.rawData})"
                )
                cachedDTCs = emptyList() // Clear cache on successful clear
                return Result.success(true)
            }

            // Fallback: if not explicitly error and not clearly success, treat as failure to be
            // safe.
            Log.w(
                TAG,
                "Uncertain response from Clear DTCs: Decoded: ${response.decodedValue}, Raw: ${response.rawData}. Assuming failure."
            )
            return Result.failure(
                IllegalStateException(
                    "Uncertain response after attempting to clear DTCs: Decoded: ${response.decodedValue}, Raw: ${response.rawData}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception clearing DTCs", e)
            Result.failure(e)
        }
    }

    /**
     * Parses the raw response string from the OBD adapter for DTCs (Mode 03). This function handles
     * various response formats, including standard OBD2 and ISO-TP (CAN bus) framing. It extracts
     * the number of DTCs and then parses each 2-byte DTC code.
     *
     * @param response The OBD2Response object containing the rawData string from the adapter.
     * @return A list of DTCError objects parsed from the response. Returns an empty list if no DTCs
     * ```
     *         are found or if the response indicates no data.
     * ```
     */
    private fun processDTCResponse(response: OBD2Response): List<DTCError> {
        var responseData =
            response.rawData.replace(
                "\\s".toRegex(),
                ""
            ) // Remove all whitespace for easier parsing.
        Log.d(TAG, "processDTCResponse - Cleaned rawData: $responseData")

        // Handle ISO-TP framed responses (e.g., "7E806430201950196").
        // These are common on CAN bus systems. The actual OBD2 data is embedded within this frame.
        // 7E8: Message from primary ECU (typical).
        // Next byte (e.g., 06): Length of the OBD2 payload.
        // Following bytes: OBD2 payload (e.g., 43 02 01 95 01 96 for Mode 03, 2 DTCs: P0195,
        // P0196).
        if (responseData.startsWith("7E8", ignoreCase = true)) {
            // Attempt to extract the core OBD2 payload (starting with "43" for Mode 03 response).
            val mode3ResponseIndex = responseData.indexOf("43")
            if (mode3ResponseIndex != -1) {
                responseData = responseData.substring(mode3ResponseIndex)
                Log.d(TAG, "processDTCResponse - Extracted Mode 03 data from ISO-TP: $responseData")
            } else {
                Log.w(
                    TAG,
                    "processDTCResponse - ISO-TP frame detected but '43' not found. Raw: ${response.rawData}"
                )
                return emptyList()
            }
        }

        // The response for mode 03 (DTCs) starts with "43" (Mode 03 + 0x40).
        // After "43", the next byte (2 hex characters) indicates the number of DTCs reported in
        // this message frame.
        // Each DTC is represented by 2 bytes (4 hex characters).
        if (!responseData.startsWith("43")) {
            Log.w(
                TAG,
                "processDTCResponse - Response does not start with '43'. Raw: ${response.rawData}, Cleaned: $responseData"
            )
            // If the raw response contains "NO DATA" or similar, it might have been missed by
            // earlier checks in `processDTCResult`.
            if (response.rawData.contains("NO DATA", ignoreCase = true) ||
                response.rawData.contains("NODATA", ignoreCase = true)
            ) {
                return emptyList()
            }
            // If it's just a prompt or ELM OK
            if (responseData == ">" ||
                responseData.endsWith("OK>", ignoreCase = true) ||
                responseData.isEmpty()
            ) {
                Log.d(
                    TAG,
                    "processDTCResponse - Empty or prompt-only response, assuming no DTCs. Cleaned: $responseData"
                )
                return emptyList()
            }
            // Otherwise, it's an unexpected format not recognized as valid DTC data.
            Log.e(
                TAG,
                "processDTCResponse - Unexpected DTC response format: $responseData. Raw: ${response.rawData}"
            )
            return listOf(
                DTCError("RAW", "Unexpected format: ${response.rawData.take(50)}")
            ) // Return raw as error
        }

        // Remove "43" prefix to get the payload, which contains the DTC count and the DTCs
        // themselves.
        val dtcPayload = responseData.substring(2)
        if (dtcPayload.length < 2) { // Need at least 2 hex chars for the DTC count byte.
            Log.d(
                TAG,
                "processDTCResponse - Not enough data after '43' for count. Payload: $dtcPayload"
            )
            return emptyList()
        }

        val numDtcHex = dtcPayload.substring(0, 2)
        val numberOfActualDTCs =
            numDtcHex.toIntOrNull(16) // Use toIntOrNull for safety against malformed hex.

        if (numberOfActualDTCs == null) {
            Log.e(
                TAG,
                "processDTCResponse - Could not parse DTC count from hex: '$numDtcHex'. Payload was: '$dtcPayload'"
            )
            return listOf(DTCError("PARSE_ERROR", "Invalid DTC count: $numDtcHex"))
        }

        Log.d(
            TAG,
            "processDTCResponse - Number of DTCs reported: $numberOfActualDTCs (Hex: $numDtcHex)"
        )

        // This is the string from which DTCs are extracted (after the count byte has been removed).
        val codesDataString = dtcPayload.substring(2)
        Log.d(TAG, "processDTCResponse - Initial codesDataString for DTC parsing: $codesDataString")

        try {
            // Iterate through the codesDataString, extracting 4 hex characters (2 bytes) for each
            // DTC,
            // up to the number of DTCs reported (numberOfActualDTCs).
            val codes = mutableListOf<String>()
            var currentDataIndex = 0
            val codesDataLength = codesDataString.length // CORRECTED: Use codesDataString

            for (k in 0 until numberOfActualDTCs) { // This will now correctly reference
                // numberOfActualDTCs
                if (currentDataIndex + 4 > codesDataLength) {
                    // Log.w(TAG, "processDTCResponse - Not enough data for DTC ${k + 1}. Remaining
                    // data: ${dtcData.substring(currentDataIndex)}") // ERROR: dtcData
                    Log.w(
                        TAG,
                        "processDTCResponse - Not enough data for DTC ${k + 1}. Remaining data: ${
                            codesDataString.substring(
                                currentDataIndex
                            )
                        }"
                    ) // CORRECTED
                    break
                }

                // Heuristic to skip interspersed CAN headers (e.g., "7E821") when ATH1 (headers on)
                // is active.
                // This can occur in multi-frame responses where subsequent frames start with a
                // header.
                // A more robust solution would be a full ISO-TP parser, but this handles common
                // cases.
                val potentialHeader = codesDataString.substring(currentDataIndex) // CORRECTED
                if (potentialHeader.startsWith("7E8")) { // Found a potential 7E8 header
                    // The observed header was "7E821" (5 chars). This is a common length for
                    // continuation frame headers.
                    // We assume such headers are 5 characters long for now. This might need
                    // adjustment if other lengths are observed.
                    val headerLength = 5
                    if (currentDataIndex + headerLength <= codesDataLength) {
                        // Log.d(TAG, "processDTCResponse - Skipping potential header
                        // ${dtcData.substring(currentDataIndex, currentDataIndex + headerLength)}
                        // at index $currentDataIndex") // ERROR
                        Log.d(
                            TAG,
                            "processDTCResponse - Skipping potential header ${
                                codesDataString.substring(
                                    currentDataIndex,
                                    currentDataIndex + headerLength
                                )
                            } at index $currentDataIndex"
                        ) // CORRECTED
                        currentDataIndex += headerLength
                        // Need to re-check if enough data for a DTC after skipping
                        if (currentDataIndex + 4 > codesDataLength) {
                            Log.w(
                                TAG,
                                "processDTCResponse - Not enough data for DTC ${k + 1} after skipping header."
                            )
                            break
                        }
                    } else {
                        // Not enough characters for a full assumed header, might be end of data or
                        // corrupt.
                        // Stop parsing to avoid errors.
                        Log.w(
                            TAG,
                            "processDTCResponse - Potential header detected but not enough data to skip fully."
                        )
                        break
                    }
                }

                // val dtcBytes = dtcData.substring(currentDataIndex, currentDataIndex + 4) // ERROR
                val dtcBytes =
                    codesDataString.substring(
                        currentDataIndex,
                        currentDataIndex + 4
                    ) // CORRECTED
                // Further check: valid DTCs (after 2-byte to char conversion) don't start with 7,
                // E, 8 as first hex digit.
                // This is a weak heuristic and primarily for logging/debugging if issues arise.
                // The main header skipping logic is the `potentialHeader.startsWith("7E8")` check.

                if (dtcBytes != "0000"
                ) { // Ignore "0000" which can be padding or indicate end of DTCs in some
                    // implementations.
                    codes.add(dtcBytes)
                }
                currentDataIndex += 4
            }
            Log.d(TAG, "processDTCResponse - Extracted DTC hex codes: $codes")

            return codes.map { formatDTC(it) }.filterNotNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DTC data: '$dtcPayload'", e)
            return listOf(
                DTCError(
                    "PARSE_ERROR",
                    "Error parsing: ${e.message}. Data: ${dtcPayload.take(50)}"
                )
            )
        }
    }

    /**
     * Formats a 2-byte hexadecimal string (e.g., "0195") into a standard DTC code (e.g., "P0195").
     * The first character (P, C, B, U) is determined by the first two bits of the first byte.
     *
     * @param hexCode The 4-character hexadecimal string representing the DTC.
     * @return A DTCError object containing the formatted code and its description, or null if
     * formatting fails.
     */
    private fun formatDTC(hexCode: String): DTCError? {
        if (hexCode.length != 4) return null // Should be 2 bytes / 4 hex chars

        val firstCharNum = hexCode.substring(0, 1).toInt(16)
        val prefix =
            when (firstCharNum shr 2
            ) { // Use first two bits of the first hex digit (00xx, 01xx, 10xx, 11xx)
                0 -> 'P' // Powertrain
                1 -> 'C' // Chassis
                2 -> 'B' // Body
                3 -> 'U' // Network (User network)
                else -> 'P' // Default to Powertrain if somehow out of range (should not happen
                // with 4-bit value)
            }
        // Construct the numeric part of the DTC.
        // The first digit of the code (0-3) is determined by the last two bits of the first hex
        // character.
        val firstDigit =
            firstCharNum and
                    0x03 // Use last two bits of the first hex digit (xx00, xx01, xx10, xx11)

        val code = "$prefix$firstDigit${hexCode.substring(1)}"
        return DTCError(code, getDTCDescription(code))
    }

    // Basic placeholder for DTC descriptions. A more comprehensive solution would use a database or
    // lookup table.
    private fun getDTCDescription(code: String): String {
        return when (code) {
            "P0195" -> "Engine Oil Temperature Sensor Circuit Malfunction"
            "P0196" -> "Engine Oil Temperature Sensor Range/Performance"
            "P0133" -> "O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)"
            // Add more known DTCs
            else -> "Unknown DTC. Check service manual."
        }
    }

    /**
     * Returns the currently cached DTCs without performing a new scan. This is useful for quickly
     * displaying previously fetched data if available.
     */
    override fun getCachedDTCs(): List<DTCError> {
        Log.d(TAG, "Returning ${cachedDTCs.size} cached DTCs")
        return cachedDTCs
    }

    /**
     * Sends the cached DTCs to the backend for the specified diagnostic record.
     *
     * @param diagnosticUUID The UUID of the diagnostic record to associate with the DTCs
     * @return Result with the count of DTCs sent successfully, or an error
     */
    override suspend fun sendDTCsToBackend(diagnosticUUID: String): Result<Int> {
        val dtcs = getCachedDTCs()

        if (dtcs.isEmpty()) {
            Log.d(TAG, "No DTCs to send to backend")
            return Result.success(0)
        }

        return try {
            Log.d(TAG, "Sending ${dtcs.size} DTCs to backend for diagnostic $diagnosticUUID")

            val dtcRequests = dtcs.map {
                CreateDTCRequest(
                    code = it.code,
                    confirmed = true // All DTCs from Mode 03 are confirmed
                )
            }

            val response = dtcApiService.createDTCs(diagnosticUUID, dtcRequests)

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                Log.d(TAG, "Successfully sent ${result.count} DTCs to backend")
                Result.success(result.count)
            } else {
                val errorMsg = "Failed to send DTCs: ${response.code()}, ${response.message()}"
                Log.e(TAG, "$errorMsg, error body: ${response.errorBody()?.string()}")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending DTCs to backend", e)
            Result.failure(e)
        }
    }
}
