package com.carsense.features.obd2.data

import android.util.Log
import com.carsense.features.obd2.domain.constants.OBD2Constants
import com.carsense.features.sensors.domain.command.RPMCommand
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/** Service that handles direct communication with the ELM327 OBD2 adapter */
class OBD2Service(private val inputStream: InputStream, private val outputStream: OutputStream) {
    companion object {
        private const val TAG = "OBD2Service"
        private const val PROMPT = ">" // ELM327 prompt character indicating end of response
        private const val CR = "\r" // Carriage Return
        private const val LF = "\n" // Line Feed
        private const val MAX_RETRIES = 3 // Max retries for sending a command
        private const val RETRY_DELAY_MS = 1000L
        private const val MAX_RESPONSE_BUFFER_SIZE = 1024
    }

    /**
     * Determines the expected prefix for a response based on the command sent. This helps in
     * identifying and correlating responses to their specific commands.
     *
     * @param command The command string that was sent to the OBD2 adapter.
     * @return The expected prefix string. For AT commands, this is often empty,
     * ```
     *         as their responses are varied and don't follow a strict echo pattern.
     *         For OBD2 mode commands, it's typically the mode (incremented by 0x40)
     *         plus the PID.
     * ```
     */
    private fun getExpectedResponsePrefix(command: String): String {
        val isAtCommand = OBD2Constants.isAtCommand(command)
        return if (isAtCommand) {
            // AT commands (like "ATI", "ATZ", "ATE0") have varied responses.
            // "ATI" -> "ELM327 v1.5" (doesn't contain "ATI")
            // "ATZ" -> "ELM327 v1.5" (might also include echoed ATZ)
            // "ATE0" -> "ATE0\rOK" or just "OK"
            // It's safest to use an empty prefix for AT commands and rely on the ">" prompt
            // to determine the end of the response. The logic in executeCommand for AT already
            // handles emitting the full segment before the prompt.
            ""
        } else {
            // For OBD2 commands (e.g., "010C", "03", "0A01")
            // The response mode is (requested mode + 0x40).
            // The command string typically starts with a 2-character hex mode.
            if (command.length >= 2) {
                val modeHex = command.substring(0, 2)
                // Corrected PID extraction:
                // If command is like "010C1" (length 5), PID is "0C".
                // If command is like "010C" (length 4), PID is "0C".
                // If command is like "03" (length 2), PID is "".
                val pidHex =
                    when {
                        // Check non-AT command explicitly here as AT command could be 5 chars
                        !isAtCommand && command.length >= 5 ->
                            command.substring(2, 4) // e.g., from "010C1", take "0C"
                        !isAtCommand && command.length >= 4 ->
                            command.substring(2, 4) // e.g., from "010C", take "0C"
                        // for mode-only commands like "03", pidHex should be empty
                        // or AT commands that fell through and are not 4/5 chars but >=2
                        else -> ""
                    }

                modeHex.toIntOrNull(16)?.let { modeInt ->
                    val responseModeInt =
                        modeInt + 0x40 // OBD2 standard: response mode = request mode + 0x40
                    val responseModeHex = responseModeInt.toString(16).uppercase()
                    return responseModeHex + pidHex
                }
                // Fallback if mode parsing fails (e.g., command is not hex or too short)
                Log.w(
                    TAG,
                    "getExpectedResponsePrefix: Could not parse mode from command '$command'. Using command as fallback prefix."
                )
                return command
            } else {
                // Command is too short to be a standard OBD2 mode+pid command
                Log.w(
                    TAG,
                    "getExpectedResponsePrefix: Command '$command' is too short. Using command as fallback prefix."
                )
                return command
            }
        }
    }

    private var isConnected = true

    /** Validates the connection to the OBD adapter by sending a simple CR and then "ATI". */
    private suspend fun validateConnection(): Boolean {
        if (!isConnected) {
            return false
        }

        try {
            // Ping with a simple AT command
            val pingCommand = CR.toByteArray()
            outputStream.write(pingCommand)
            outputStream.flush()

            // Wait a bit for response
            delay(500)

            // Check if there's any response
            if (inputStream.available() > 0) {
                return true
            }

            // Try with ATI command (identify adapter)
            return sendCommand("ATI")
        } catch (e: IOException) {
            Log.e(TAG, "Connection validation failed: ${e.message}")
            isConnected = false
            return false
        }
    }

    /**
     * Sends a command to the OBD adapter. This method includes logic to clear stale data from the
     * input stream before sending the command and implements a retry mechanism.
     *
     * @param command The command string to send.
     * @return True if the command was sent successfully, false otherwise.
     */
    suspend fun sendCommand(command: String): Boolean {
        return withContext(Dispatchers.IO) {
            var retries = 0
            var success = false

            while (retries < MAX_RETRIES && !success) {
                try {
                    if (!isConnected) {
                        Log.e(TAG, "Cannot send command - not connected")

                        // Try to restore connection
                        if (retries == 0) {
                            Log.d(TAG, "Attempting to restore connection")
                            isConnected = validateConnection()
                            if (!isConnected) {
                                return@withContext false
                            }
                        } else {
                            return@withContext false
                        }
                    }

                    Log.d(TAG, "Sending command: $command")

                    // Clear any stale data from the input stream before sending new command.
                    // This is crucial to prevent previous, unread responses from interfering
                    // with the current command's response.
                    try {
                        var available = inputStream.available()
                        if (available > 0) {
                            Log.d(
                                TAG,
                                "sendCommand ('$command'): Clearing $available stale bytes from input stream..."
                            )
                            val staleDataBuffer = ByteArray(2048) // Max buffer to clear at once
                            while (available > 0) {
                                val bytesRead =
                                    inputStream.read(
                                        staleDataBuffer,
                                        0,
                                        java.lang.Math.min(available, staleDataBuffer.size)
                                    )
                                if (bytesRead <= 0) {
                                    Log.w(
                                        TAG,
                                        "sendCommand ('$command'): Read $bytesRead bytes while clearing stale data. Breaking clear loop. Available was $available."
                                    )
                                    break
                                }
                                Log.d(
                                    TAG,
                                    "sendCommand ('$command'): Cleared $bytesRead stale bytes segment: " +
                                            String(staleDataBuffer, 0, bytesRead)
                                                .replace("\\r", "\\\\r")
                                                .replace("\\n", "\\\\n")
                                                .replace(">", "[PROMPT]")
                                )
                                delay(
                                    1L
                                ) // Brief pause to allow more data to arrive if stream is active
                                available = inputStream.available()
                            }
                            Log.d(TAG, "sendCommand ('$command'): Finished clearing stale bytes.")
                        }
                    } catch (e: IOException) {
                        Log.w(
                            TAG,
                            "sendCommand ('$command'): IOException while clearing stale input stream: ${e.message}"
                        )
                    }

                    // Add carriage return and line feed to the command - just use CR for faster
                    // response
                    val fullCommand = "$command$CR"
                    val commandBytes = fullCommand.toByteArray()
                    outputStream.write(commandBytes)
                    outputStream.flush()
                    success = true

                    // Reduced delay to minimum necessary for command processing
                    // delay(50) // Reduced from 100ms // Commenting out for faster polling
                    // experiment
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending command: ${e.message}")
                    e.printStackTrace()
                    retries++
                    isConnected = false

                    if (retries < MAX_RETRIES) {
                        Log.d(TAG, "Retrying command in ${RETRY_DELAY_MS / 2}ms")
                        delay(RETRY_DELAY_MS / 2) // Reduced delay for faster retry
                    }
                }
            }

            success
        }
    }

    /** Listen for responses from the OBD adapter */
    @Deprecated("This method is being refactored. Use executeCommand instead.")
    fun listenForResponses(): Flow<OBD2Response> {
        return flow {
            // This flow is problematic due to shared state (lastCommand).
            // It should be redesigned or its logic incorporated into a new
            // command execution mechanism that properly correlates requests and responses.
            Log.w(
                TAG,
                "listenForResponses() is deprecated and will be removed or refactored."
            )
            if (!isConnected) {
                Log.e(TAG, "Cannot listen for responses - not connected")
                val errorResponse =
                    OBD2Response.createError("DEPRECATED", "Not connected to adapter")
                emit(errorResponse)
                return@flow
            }

            // The old implementation is left here for reference during refactoring,
            // but it should not be used.
            // ... (old problematic implementation was here) ...
            emit(
                OBD2Response.createError(
                    "DEPRECATED",
                    "Functionality moved to executeCommand"
                )
            )
        }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Executes an OBD2 command and returns a Flow of its specific responses. This function handles
     * sending the command and then processing the input stream to find and parse only the
     * response(s) relevant to the sent command. It manages timeouts and various response types
     * including standard OBD, AT commands, and error conditions.
     *
     * @param command The OBD2 command to send (e.g., "010C" for RPM, "ATI" for adapter info).
     * @return A Flow emitting OBD2Response objects for the given command.
     * ```
     *         The flow will complete after emitting the relevant response(s) or an error/timeout.
     * ```
     */
    fun executeCommand(command: String): Flow<OBD2Response> {
        return flow {
            Log.d(TAG, "executeCommand: Starting for command '$command'")
            if (!isConnected) {
                Log.e(TAG, "executeCommand: Not connected, cannot send command '$command'")
                emit(OBD2Response.createError(command, "Not connected"))
                return@flow
            }

            if (!sendCommand(command)) { // sendCommand is a suspend function
                Log.e(TAG, "executeCommand: Failed to send command '$command'")
                emit(OBD2Response.createError(command, "Failed to send command"))
                return@flow
            }

            val isAtCommand = command.startsWith("AT", ignoreCase = true)

            val expectedResponsePrefix = getExpectedResponsePrefix(command)

            val buffer = ByteArray(MAX_RESPONSE_BUFFER_SIZE)
            val accumulatedResponse = StringBuilder()
            var continueReading = true
            val commandTimeoutMs =
                5000L // Timeout for waiting for a response for this specific command
            var elapsedTimeMs = 0L
            val readDelayMs = 5L // How long to wait between reads if no data

            while (continueReading && elapsedTimeMs < commandTimeoutMs) {
                try {
                    if (inputStream.available() > 0) {
                        val byteCount = inputStream.read(buffer)
                        if (byteCount > 0) {
                            val rawChunk = buffer.decodeToString(endIndex = byteCount)
                            Log.d(
                                TAG,
                                "executeCommand ('$command'): Read $byteCount bytes: \"$rawChunk\""
                            )
                            accumulatedResponse.append(
                                rawChunk.replace(CR, "").replace(LF, "")
                            )

                            // Check for prompt character, indicating end of a full ELM327
                            // response block
                            if (accumulatedResponse.contains(PROMPT)) {
                                val responses = accumulatedResponse.toString().split(PROMPT)
                                accumulatedResponse
                                    .clear() // Clear buffer for next potential partial
                                // response

                                // Process each segment separated by the prompt character.
                                // A single read might contain multiple such segments.
                                for (i in responses.indices) {
                                    val singleFullResponse = responses[i].trim()
                                    if (singleFullResponse.isEmpty()) continue

                                    Log.d(
                                        TAG,
                                        "executeCommand ('$command'): Processing complete segment: \"$singleFullResponse\""
                                    )

                                    // For AT commands with an empty expectedResponsePrefix,
                                    // any data received before the prompt is considered the
                                    // response.
                                    if (isAtCommand && expectedResponsePrefix.isEmpty()) {
                                        Log.d(
                                            TAG,
                                            "executeCommand ('$command'): AT command with empty prefix, accepting segment: \"$singleFullResponse\""
                                        )
                                        val atResponse =
                                            OBD2Response(
                                                command = command,
                                                rawData = singleFullResponse,
                                                decodedValue =
                                                    singleFullResponse, // For
                                                // AT,
                                                // raw
                                                // is
                                                // often
                                                // the
                                                // decoded value
                                                unit = "",
                                                isError = false
                                            )
                                        emit(atResponse)
                                        continueReading = false // Found our response
                                        break // Exit loop over split responses
                                    } else if (singleFullResponse
                                            .replace(" ", "")
                                            .indexOf(
                                                expectedResponsePrefix.replace(
                                                    " ",
                                                    ""
                                                ),
                                                startIndex = 0,
                                                ignoreCase = true
                                            ) != -1
                                    ) {
                                        Log.i(
                                            TAG,
                                            "executeCommand ('$command'): Matched response \"$singleFullResponse\""
                                        )
                                        val decoded =
                                            OBD2Decoder.decodeResponse(
                                                command,
                                                singleFullResponse
                                            )
                                        emit(decoded)
                                        continueReading = false // Found our response
                                        break // Exit loop over split responses
                                    } else if (command ==
                                        OBD2Constants
                                            .CLEAR_DTC_COMMAND_STRING &&
                                        singleFullResponse
                                            .trim()
                                            .equals(
                                                "NO DATA",
                                                ignoreCase = true
                                            )
                                    ) {
                                        Log.i(
                                            TAG,
                                            "executeCommand ('$command'): Received 'NO DATA' for Mode 04 (Clear DTCs), treating as success."
                                        )
                                        emit(
                                            OBD2Response(
                                                command = command,
                                                rawData = singleFullResponse,
                                                decodedValue = "NO DATA",
                                                unit = "",
                                                isError = false
                                            )
                                        )
                                        continueReading = false
                                        break
                                    } else if (singleFullResponse.contains(
                                            "NODATA", // Note: "NO DATA" (with
                                            // space) handled above for
                                            // "04" specifically
                                            ignoreCase = true
                                        ) ||
                                        singleFullResponse.contains(
                                            "UNABLETOCONNECT",
                                            ignoreCase = true
                                        ) ||
                                        singleFullResponse.contains(
                                            "CANERROR",
                                            ignoreCase = true
                                        ) ||
                                        singleFullResponse.contains(
                                            "BUSINIT",
                                            ignoreCase = true
                                        ) || // Often followed by ERROR
                                        singleFullResponse.contains(
                                            "?",
                                            ignoreCase = true
                                        ) // ELM327 often sends '?' for unknown
                                    // command
                                    ) {
                                        Log.w(
                                            TAG,
                                            "executeCommand ('$command'): Received error/status: \"$singleFullResponse\""
                                        )
                                        val errorMsg =
                                            "Device responded: $singleFullResponse"
                                        // Check if it's an error for the *current* command
                                        // Some errors like '?' are generic. Others might be
                                        // specific if the prefix matches.
                                        if (singleFullResponse
                                                .replace(" ", "")
                                                .startsWith(
                                                    expectedResponsePrefix
                                                        .replace(" ", ""),
                                                    ignoreCase = false
                                                ) ||
                                            !OBD2Constants.isAtCommand(
                                                command
                                            ) // Apply to PID commands more
                                        // strictly
                                        ) {
                                            emit(
                                                OBD2Response.createError(
                                                    command,
                                                    errorMsg
                                                )
                                            )
                                            continueReading = false
                                            break
                                        } else {
                                            // It's some other status message not directly
                                            // for our command, log and continue waiting
                                            Log.d(
                                                TAG,
                                                "executeCommand ('$command'): Ignoring intermediate status: \"$singleFullResponse\""
                                            )
                                        }
                                    } else if (singleFullResponse.contains(
                                            "SEARCHING...",
                                            ignoreCase = true
                                        )
                                    ) {
                                        Log.d(
                                            TAG,
                                            "executeCommand ('$command'): Device is searching, continue waiting..."
                                        )
                                        // continue waiting for actual data or prompt
                                    } else {
                                        Log.d(
                                            TAG,
                                            "executeCommand ('$command'): Received non-matching/intermediate data: \"$singleFullResponse\" - discarding or queueing for other handlers if any."
                                        )
                                        // This part is tricky in a shared input stream
                                        // scenario.
                                        // For a per-command reader, we might discard if
                                        // it's not ours.
                                        // If other commands are truly concurrent, this data
                                        // might belong to them.
                                    }
                                }
                                // If the last part of split was partial (no prompt yet),
                                // put it back
                                if (!rawChunk.endsWith(PROMPT) && responses.isNotEmpty()) {
                                    if (responses.last().isNotEmpty()) {
                                        accumulatedResponse.append(responses.last())
                                    }
                                }
                            }
                        } else { // byteCount <= 0, possibly end of stream if socket closed
                            delay(readDelayMs)
                            elapsedTimeMs += readDelayMs
                        }
                    } else { // No data available
                        delay(readDelayMs)
                        elapsedTimeMs += readDelayMs
                    }
                } catch (e: CancellationException) {
                    Log.d(
                        TAG,
                        "executeCommand ('$command'): Coroutine cancelled during/after emission",
                        e
                    )
                    throw e // Rethrow cancellation exceptions
                } catch (e: IOException) {
                    Log.e(TAG, "executeCommand ('$command'): IOException: ${e.message}", e)
                    emit(OBD2Response.createError(command, "IOException: ${e.message}"))
                    continueReading = false
                    isConnected = false // Assume connection lost
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "executeCommand ('$command'): Generic Exception: ${e.message}",
                        e
                    )
                    emit(OBD2Response.createError(command, "Exception: ${e.message}"))
                    continueReading = false
                }
            }

            if (continueReading && elapsedTimeMs >= commandTimeoutMs) {
                Log.w(
                    TAG,
                    "executeCommand ('$command'): Timed out after ${commandTimeoutMs}ms"
                )
                emit(OBD2Response.createError(command, "Timeout waiting for response"))
            }
            Log.d(TAG, "executeCommand: Finished for command '$command'")
        }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Returns the current connection state
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean {
        return isConnected
    }

    /**
     * Initializes the OBD2 adapter with a sequence of setup AT commands. This typically includes
     * resetting the adapter, turning echo off, setting automatic protocol detection, etc.
     *
     * @return True if initialization sequence was sent successfully, false otherwise.
     */
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting ELM327 initialization")
            var success = false

            try {
                // First check connection status
                if (!validateConnection()) {
                    Log.e(TAG, "Connection validation failed")
                    return@withContext false
                }

                // Attempt ELM327 initialization sequence
                try {
                    // Shorter wait before initializing
                    delay(1000) // Reduced from 2000ms

                    // Send a few carriage returns to reset any pending command from the ELM327.
                    for (i in 1..2) { // Reduced from 3 to 2
                        outputStream.write(CR.toByteArray())
                        outputStream.flush()
                        delay(100) // Reduced from 200ms
                    }

                    // Reset the ELM327 to its default state.
                    Log.d(TAG, "Sending reset command")
                    success = sendCommand(OBD2Constants.RESET_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Reset command failed")
                        return@withContext false
                    }
                    delay(1500) // Reduced from 2000ms but still need enough time for reset

                    // Use a combined command string for faster setup
                    val setupCommands =
                        listOf(
                            OBD2Constants.ECHO_OFF_COMMAND,
                            OBD2Constants.LINEFEED_OFF_COMMAND,
                            OBD2Constants.HEADER_ON_COMMAND,
                            OBD2Constants.SPACES_OFF_COMMAND
                        )

                    // Send essential setup commands with minimal delay between them.
                    // These commands configure the ELM327 for optimal communication.
                    for (cmd in setupCommands) {
                        success = sendCommand(cmd)
                        if (!success) {
                            Log.w(TAG, "Setup command $cmd failed, continuing anyway")
                        }
                        delay(150) // Reduced from 300ms
                    }

                    // Set protocol to auto-detect. ELM327 will try various protocols to connect to
                    // the vehicle.
                    Log.d(TAG, "Setting protocol to auto")
                    success = sendCommand(OBD2Constants.PROTOCOL_AUTO_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Auto protocol command failed")
                        return@withContext false
                    }
                    delay(300) // Need more time for protocol setting

                    // Allow long messages, important for multi-frame responses (e.g., VIN, some DTC
                    // lists).
                    Log.d(TAG, "Allowing long messages")
                    success = sendCommand(OBD2Constants.LONG_MESSAGES_COMMAND)
                    if (!success) {
                        Log.w(TAG, "Long messages command failed, continuing anyway")
                    }

                    Log.d(TAG, "ELM327 initialization complete")
                    isConnected = true // Explicitly mark as connected after successful init
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "Initialization exception: ${e.message}")
                    e.printStackTrace()
                    isConnected = false
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initialization outer exception: ${e.message}")
                e.printStackTrace()
                isConnected = false
                return@withContext false
            }
        }
    }

    /**
     * Sends a basic OBD2 diagnostic command (e.g., RPM) to check if the vehicle is responsive.
     * @return True if the command was sent and a response is likely, false otherwise.
     * @Deprecated This method might not be fully reliable as it doesn't analyze the response.
     * ```
     *             Connection status is better managed by `isConnected` and actual command execution flows.
     * ```
     */
    @Deprecated("Use isConnected() and observe command results directly.")
    suspend fun checkVehicleConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Test with RPM command which is usually supported
                val rpmCommand = OBD2Decoder.getCommand(RPMCommand::class)?.getCommand() ?: "010C"
                val success = sendCommand(rpmCommand)

                if (!success) {
                    return@withContext false
                }

                // Wait for response
                delay(1000)

                // If we get this far, assume we're connected
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Vehicle connection check failed: ${e.message}")
                return@withContext false
            }
        }
    }
}
