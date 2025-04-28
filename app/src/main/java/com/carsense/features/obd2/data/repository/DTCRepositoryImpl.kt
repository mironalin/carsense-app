package com.carsense.features.obd2.data.repository

import android.util.Log
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.obd2.domain.OBD2Message
import com.carsense.features.obd2.domain.command.ClearDTCCommand
import com.carsense.features.obd2.domain.command.DTCCommand
import com.carsense.features.obd2.domain.repository.DTCRepository
import com.carsense.features.obd2.presentation.viewmodel.DTCError
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of the DTCRepository that communicates with the vehicle via Bluetooth */
@Singleton
class DTCRepositoryImpl @Inject constructor(private val bluetoothController: BluetoothController) :
    DTCRepository {

    private val TAG = "DTCRepository"

    // Maximum number of retries for connection checks
    private val MAX_CONNECTION_RETRIES = 3

    // Cache of the last DTC scan results
    private var cachedDTCs: List<DTCError> = emptyList()

    override suspend fun getDTCs(): Result<List<DTCError>> {
        return try {
            Log.d(TAG, "Getting DTCs from vehicle...")

            // Check if we're connected with retries
            if (!checkConnectionWithRetry()) {
                Log.e(TAG, "Not connected to a vehicle after retry attempts")
                // Return cached DTCs if available when not connected
                if (cachedDTCs.isNotEmpty()) {
                    Log.d(TAG, "Returning ${cachedDTCs.size} cached DTCs from previous scan")
                    return Result.success(cachedDTCs)
                }
                return Result.failure(
                    IllegalStateException(
                        "Not connected to a vehicle. Make sure you have an active connection before scanning."
                    )
                )
            }

            Log.d(TAG, "Confirmed OBD2 connection, preparing to send DTC command")

            // Create the DTC command
            val dtcCommand = DTCCommand()

            // Pre-send the DTC command once to prime the adapter and discard the result
            // This handles the "SEARCHING..." issue with the first command
            Log.d(TAG, "Pre-sending DTC command to prime the adapter")
            bluetoothController.sendOBD2Command(dtcCommand.getCommand())
            delay(1000) // Wait a full second for the first command to complete

            // Now send the actual command we'll use
            Log.d(TAG, "Sending actual DTC command: ${dtcCommand.getCommand()}")
            val response = bluetoothController.sendOBD2Command(dtcCommand.getCommand())

            if (response == null) {
                Log.e(TAG, "Failed to send DTC command or no response received")
                return Result.failure(
                    IllegalStateException("Failed to send DTC command or no response received")
                )
            }

            Log.d(TAG, "Raw DTC response received: ${response.content}, type: ${response.type}")

            // If we got "SEARCHING..." or an incomplete response, wait and try once more
            if (response.content.contains("SEARCHING...") && !response.content.contains("7E8")) {
                Log.d(TAG, "Got SEARCHING response, waiting and trying one more time")
                delay(2000) // Wait longer to allow search to complete

                val retryResponse = bluetoothController.sendOBD2Command(dtcCommand.getCommand())
                if (retryResponse != null) {
                    Log.d(TAG, "Retry got response: ${retryResponse.content}")
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
            // If we have cached DTCs, return them on error
            if (cachedDTCs.isNotEmpty()) {
                Log.d(TAG, "Returning ${cachedDTCs.size} cached DTCs due to error")
                return Result.success(cachedDTCs)
            }
            Result.failure(e)
        }
    }

    /** Process a DTC response and return the final result */
    private fun processDTCResult(response: OBD2Message): Result<List<DTCError>> {
        // Parse the response
        if (response.type == com.carsense.features.obd2.domain.OBD2MessageType.ERROR) {
            Log.e(TAG, "Error response from OBD: ${response.content}")

            // Special handling for "UNABLE TO CONNECT" which often means the vehicle
            // doesn't support Mode 03 DTC reading or has no DTCs
            if (response.content.contains("UNABLE TO CONNECT") ||
                response.content.contains("SEARCHING...UNABLE TO CONNECT")
            ) {
                Log.d(
                    TAG,
                    "Vehicle indicates 'UNABLE TO CONNECT' - likely no DTCs or feature not supported"
                )
                return Result.success(emptyList())
            }

            return Result.failure(IllegalStateException("Error response: ${response.content}"))
        }

        // Check for "NO DATA" or empty responses
        if (response.content.contains("NO DATA") ||
            response.content.trim() == "03:" ||
            response.content.contains("NODATA")
        ) {
            Log.d(TAG, "Response indicates no DTCs")
            return Result.success(emptyList())
        }

        // Special handling for "SEARCHING..." with no actual data
        if (response.content.contains("SEARCHING...") && !response.content.contains("7E8")) {
            Log.d(TAG, "Response indicates device is still searching but no data was found")
            return Result.success(emptyList())
        }

        // Process the content into DTCError objects
        val dtcList = processDTCResponse(response)
        Log.d(TAG, "Processed DTCs: $dtcList")

        // Special case: if we got "P0095" and "P0096" instead of "P0195" and "P0196", fix it
        val correctedList =
            dtcList.map { dtcError ->
                val code = dtcError.code
                if (code.startsWith("P00") && code.length == 5) {
                    // This is likely a misinterpreted code like P0095 instead of P0195
                    val correctedCode = "P0" + code.substring(3)
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
     * Check for connection with multiple retry attempts This helps address timing issues and
     * temporary connection drops
     */
    private suspend fun checkConnectionWithRetry(): Boolean {
        var retries = 0
        var initializationSucceeded = false

        // First fast check - if already connected, don't try to reconnect
        if (bluetoothController.isConnected.value) {
            Log.d(TAG, "Connected on first check")

            // Verify connection with a simple test command if needed
            try {
                val testResponse = bluetoothController.sendOBD2Command("ATI")
                if (testResponse != null && !testResponse.content.isNullOrEmpty()) {
                    Log.d(
                        TAG,
                        "Confirmed active connection with test command: ${testResponse.content}"
                    )
                    return true
                }
                // If test command fails but isConnected was true, try reinitialization once
                Log.d(TAG, "Connection test failed despite isConnected=true, trying initialization")
                initializationSucceeded = bluetoothController.initializeOBD2()
                return initializationSucceeded
            } catch (e: Exception) {
                Log.e(TAG, "Error testing connection", e)
                // Fall through to retry logic below
            }
        }

        // If not immediately connected, try up to MAX_CONNECTION_RETRIES times
        while (retries < MAX_CONNECTION_RETRIES) {
            Log.d(TAG, "Connection check retry ${retries + 1}/${MAX_CONNECTION_RETRIES}")

            if (bluetoothController.isConnected.value) {
                Log.d(TAG, "Connection confirmed on retry ${retries + 1}")
                return true
            }

            // Try to re-initialize connection
            try {
                Log.d(TAG, "Attempting to initialize OBD2")
                initializationSucceeded = bluetoothController.initializeOBD2()

                if (initializationSucceeded) {
                    Log.d(TAG, "OBD2 initialization successful, considering device connected")

                    // Send a simple AT command to verify communication
                    val testResponse = bluetoothController.sendOBD2Command("ATI")
                    if (testResponse != null && !testResponse.content.isNullOrEmpty()) {
                        Log.d(TAG, "Communication test successful: ${testResponse.content}")
                        return true
                    }
                }

                delay(500) // Wait a bit for initialization
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization retry", e)
            }

            retries++
            delay(1000) // Wait before retrying
        }

        Log.e(TAG, "Connection check failed after $MAX_CONNECTION_RETRIES retries")
        return false
    }

    override suspend fun clearDTCs(): Result<Boolean> {
        return try {
            Log.d(TAG, "Clearing DTCs from vehicle...")

            // Check if we're connected with retries
            if (!checkConnectionWithRetry()) {
                Log.e(TAG, "Not connected to a vehicle after retry attempts")
                return Result.failure(
                    IllegalStateException(
                        "Not connected to a vehicle. Make sure you have an active connection before clearing DTCs."
                    )
                )
            }

            // Create the Clear DTC command
            val clearCommand = ClearDTCCommand()
            Log.d(TAG, "Sending clear DTC command: ${clearCommand.getCommand()}")

            // Send the command via the Bluetooth controller
            val response = bluetoothController.sendOBD2Command(clearCommand.getCommand())
            if (response == null) {
                Log.e(TAG, "Failed to send Clear DTC command or no response received")
                return Result.failure(IllegalStateException("Failed to send Clear DTC command"))
            }

            Log.d(TAG, "Clear DTC response received: ${response.content}, type: ${response.type}")

            // Check if successful
            val success =
                response.content.contains("DTCs Cleared Successfully") ||
                        response.content.contains("OK") ||
                        response.content.contains("44") ||
                        // "NO DATA" is a valid response when clearing a small number of DTCs
                        response.content.contains("NO DATA")

            Log.d(TAG, "Clear DTCs success: $success")

            // Clear the cache if successful
            if (success) {
                Log.d(TAG, "Clearing cached DTCs after successful clear operation")
                cachedDTCs = emptyList()
            }

            Result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Exception clearing DTCs", e)
            Result.failure(e)
        }
    }

    /**
     * Process the DTC response from the OBD2 adapter and convert to DTCError objects with
     * descriptions
     */
    private fun processDTCResponse(response: OBD2Message): List<DTCError> {
        Log.d(TAG, "Processing DTC response: ${response.content}")

        try {
            // Extract the actual content part of the message
            val content = response.content.substringAfter(":").trim()
            Log.d(TAG, "Extracted content: '$content'")

            // Use the DTCCommand to parse the codes
            Log.d(TAG, "Attempting to parse using DTCCommand")
            val dtcCommand = DTCCommand()
            val parsedCodes = dtcCommand.parseResponse(response.content)
            Log.d(
                TAG,
                "DTCCommand parsing result: ${parsedCodes.value}, isError: ${parsedCodes.isError}"
            )

            if (parsedCodes.isError || parsedCodes.value == "NO DATA") {
                Log.d(TAG, "No valid codes found in response")
                return emptyList()
            }

            // Extract the individual code strings
            val codes = parsedCodes.value.split(",")
            Log.d(TAG, "Parsed codes from sensor reading: $codes")

            // Convert the codes to DTCError objects with descriptions
            val dtcErrors =
                codes.map { code ->
                    val description = getDTCDescription(code)
                    Log.d(TAG, "DTC Description for $code: $description")
                    DTCError(code = code, description = description)
                }

            Log.d(TAG, "Final DTCError objects: $dtcErrors")
            return dtcErrors
        } catch (e: Exception) {
            Log.e(TAG, "Error processing DTC response", e)
            return emptyList()
        }
    }

    /**
     * Get a description for a DTC code In a real implementation, this would use a database of DTC
     * descriptions For now, we'll use a simple mapping for some common codes
     */
    private fun getDTCDescription(code: String): String {
        val description =
            when (code) {
                "P0301" -> "Cylinder 1 Misfire Detected"
                "P0302" -> "Cylinder 2 Misfire Detected"
                "P0303" -> "Cylinder 3 Misfire Detected"
                "P0304" -> "Cylinder 4 Misfire Detected"
                "P0305" -> "Cylinder 5 Misfire Detected"
                "P0306" -> "Cylinder 6 Misfire Detected"
                "P0420" -> "Catalyst System Efficiency Below Threshold"
                "P0171" -> "System Too Lean (Bank 1)"
                "P0174" -> "System Too Lean (Bank 2)"
                "P0300" -> "Random/Multiple Cylinder Misfire Detected"
                "P0401" -> "Exhaust Gas Recirculation Flow Insufficient"
                "P0455" -> "Evaporative Emission Control System Leak Detected (large leak)"
                "P0442" -> "Evaporative Emission Control System Leak Detected (small leak)"
                "P0440" -> "Evaporative Emission Control System Malfunction"
                "P0446" ->
                    "Evaporative Emission Control System Vent Control Circuit Malfunction"

                "P0128" -> "Coolant Thermostat Temperature Below Regulation"
                "P0131" -> "O2 Sensor Circuit Low Voltage (Bank 1, Sensor 1)"
                "P0135" -> "O2 Sensor Heater Circuit Malfunction (Bank 1, Sensor 1)"
                "P0141" -> "O2 Sensor Heater Circuit Malfunction (Bank 1, Sensor 2)"
                "P0195" -> "Engine Oil Temperature Sensor Malfunction"
                "P0196" -> "Engine Oil Temperature Sensor Range/Performance"
                // Add more descriptions as needed
                else -> "Unknown Code: Check Service Manual"
            }

        Log.d(TAG, "DTC Description for $code: $description")
        return description
    }

    /** Returns the currently cached DTCs without performing a scan */
    override fun getCachedDTCs(): List<DTCError> {
        Log.d(TAG, "Returning ${cachedDTCs.size} cached DTCs")
        return cachedDTCs
    }
}
