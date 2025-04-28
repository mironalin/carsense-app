package com.carsense.features.obd2.data

import android.util.Log
import com.carsense.features.obd2.domain.constants.OBD2Constants
import com.carsense.features.sensors.domain.command.RPMCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Service that handles direct communication with the ELM327 OBD2 adapter */
class OBD2Service(private val inputStream: InputStream, private val outputStream: OutputStream) {
    companion object {
        private const val TAG = "OBD2Service"
        private const val PROMPT = ">"
        private const val CR = "\r"
        private const val LF = "\n"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private var lastCommand: String = ""
    private var isConnected = true

    // Store the last raw response
    private var lastRawResponse: String = ""

    /** Validates the connection to the OBD adapter */
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

    /** Sends a command to the OBD adapter */
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

                    // Store command for response pairing
                    lastCommand = command
                    Log.d(TAG, "Sending command: $command")

                    // Add carriage return and line feed to the command
                    val fullCommand = "$command$CR$LF"
                    outputStream.write(fullCommand.toByteArray())
                    outputStream.flush()
                    success = true

                    // Slight delay to ensure command is sent
                    delay(100)
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending command: ${e.message}")
                    e.printStackTrace()
                    retries++
                    isConnected = false

                    if (retries < MAX_RETRIES) {
                        Log.d(TAG, "Retrying command in ${RETRY_DELAY_MS}ms")
                        delay(RETRY_DELAY_MS)
                    }
                }
            }

            success
        }
    }

    /** Listen for responses from the OBD adapter */
    fun listenForResponses(): Flow<OBD2Response> {
        return flow {
            if (!isConnected) {
                Log.e(TAG, "Cannot listen for responses - not connected")
                val errorResponse = OBD2Response.createError("", "Not connected to adapter")
                emit(errorResponse)
                return@flow
            }

            val buffer = StringBuilder()
            val responseBuffer = ByteArray(1024)

            try {
                while (isConnected) {
                    try {
                        val available = inputStream.available()
                        if (available <= 0) {
                            delay(100) // Avoid tight polling loop
                            continue
                        }

                        val byteCount =
                            try {
                                val count = inputStream.read(responseBuffer)
                                Log.d(TAG, "Read $count bytes from socket")
                                count
                            } catch (e: IOException) {
                                Log.e(TAG, "Error reading from socket: ${e.message}")
                                isConnected = false
                                throw IOException("Failed to read from OBD adapter", e)
                            }

                        if (byteCount <= 0) {
                            continue
                        }

                        val response = responseBuffer.decodeToString(endIndex = byteCount)
                        Log.d(TAG, "Received raw data: $response")
                        buffer.append(response)

                        // Process complete responses (ending with prompt)
                        // ELM327 responses end with '>' character
                        while (buffer.contains(PROMPT)) {
                            val endIndex = buffer.indexOf(PROMPT)
                            val completeResponse =
                                buffer.substring(0, endIndex)
                                    .trim()
                                    .replace(CR, "")
                                    .replace(LF, "")
                                    .replace("\\s+".toRegex(), " ")

                            if (completeResponse.isNotEmpty()) {
                                Log.d(
                                    TAG,
                                    "Complete response for $lastCommand: $completeResponse"
                                )

                                // Store the raw response
                                lastRawResponse = completeResponse

                                // Check for error responses that indicate connection issues
                                if (completeResponse.contains(
                                        "UNABLE TO CONNECT",
                                        ignoreCase = true
                                    )
                                ) {
                                    // Don't mark as disconnected - this often means the vehicle doesn't
                                    // support a feature, not a connection issue with the adapter
                                    Log.d(
                                        TAG,
                                        "UNABLE TO CONNECT response - vehicle likely doesn't support this feature"
                                    )
                                } else if (completeResponse.contains(
                                        "NO DATA",
                                        ignoreCase = true
                                    )
                                ) {
                                    Log.d(
                                        TAG,
                                        "NO DATA response - command valid but no response from vehicle"
                                    )
                                } else if (completeResponse.contains(
                                        "ERROR",
                                        ignoreCase = true
                                    ) ||
                                    completeResponse.contains(
                                        "?",
                                        ignoreCase = true
                                    )
                                ) {
                                    // These are usually command errors, not connection errors
                                    Log.d(
                                        TAG,
                                        "Command error response, but connection is still valid"
                                    )
                                }

                                // Decode the OBD2 response
                                val decodedResponse =
                                    OBD2Decoder.decodeResponse(
                                        lastCommand,
                                        completeResponse
                                    )
                                Log.d(
                                    TAG,
                                    "Decoded response: value=${decodedResponse.decodedValue}, unit=${decodedResponse.unit}, isError=${decodedResponse.isError}"
                                )
                                emit(decodedResponse)
                            }

                            buffer.delete(0, endIndex + 1)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "IO error in response listener: ${e.message}")
                        isConnected = false
                        val errorResponse =
                            OBD2Response.createError(
                                lastCommand,
                                "IO Error: ${e.message ?: "Unknown IO error"}"
                            )
                        emit(errorResponse)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in response listener: ${e.message}")
                isConnected = false
                e.printStackTrace()

                val errorResponse =
                    OBD2Response.createError(
                        lastCommand,
                        "Error: ${e.message ?: "Unknown error"}"
                    )
                emit(errorResponse)
            }
        }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Gets the last raw response received from the OBD2 adapter
     * @return The last raw response as a string
     */
    fun getLastRawResponse(): String {
        Log.d(TAG, "Getting last raw response: '$lastRawResponse'")
        return lastRawResponse
    }

    /**
     * Returns the current connection state
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean {
        return isConnected
    }

    /** Initialize the OBD2 adapter with setup commands */
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
                    // Wait a little before initializing to allow connection to stabilize
                    delay(2000)

                    // Send a few carriage returns to reset any pending command
                    for (i in 1..3) {
                        outputStream.write(CR.toByteArray())
                        outputStream.flush()
                        delay(200)
                    }

                    // Reset the ELM327
                    Log.d(TAG, "Sending reset command")
                    success = sendCommand(OBD2Constants.RESET_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Reset command failed")
                        return@withContext false
                    }
                    delay(2000) // ELM327 needs time to reset

                    // Turn off echo
                    Log.d(TAG, "Turning off echo")
                    success = sendCommand(OBD2Constants.ECHO_OFF_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Echo off command failed")
                        return@withContext false
                    }
                    delay(300)

                    // Turn off line feeds
                    Log.d(TAG, "Turning off linefeeds")
                    success = sendCommand(OBD2Constants.LINEFEED_OFF_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Linefeeds off command failed")
                        return@withContext false
                    }
                    delay(300)

                    // Turn on headers
                    Log.d(TAG, "Turning on headers")
                    success = sendCommand(OBD2Constants.HEADER_ON_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Headers on command failed")
                        return@withContext false
                    }
                    delay(300)

                    // Set protocol to auto
                    Log.d(TAG, "Setting protocol to auto")
                    success = sendCommand(OBD2Constants.PROTOCOL_AUTO_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Auto protocol command failed")
                        return@withContext false
                    }

                    // Turn off spaces
                    Log.d(TAG, "Turning off spaces")
                    success = sendCommand(OBD2Constants.SPACES_OFF_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Spaces off command failed")
                        return@withContext false
                    }

                    // Allow long messages
                    Log.d(TAG, "Allowing long messages")
                    success = sendCommand(OBD2Constants.LONG_MESSAGES_COMMAND)
                    if (!success) {
                        Log.e(TAG, "Long messages command failed")
                        return@withContext false
                    }

                    Log.d(TAG, "ELM327 initialization complete")
                    isConnected = true // Explicitly mark as connected after successful init
                    return@withContext success
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

    /** Sends OBD2 diagnostic command to check vehicle readiness */
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
