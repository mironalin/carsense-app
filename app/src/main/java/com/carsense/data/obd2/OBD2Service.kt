package com.carsense.data.obd2

import android.util.Log
import com.carsense.domain.bluetooth.TransferFailedException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class OBD2Service(private val socket: InputStream, private val outputStream: OutputStream) {
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

    private suspend fun validateConnection(): Boolean {
        if (!isConnected) {
            return false
        }

        try {
            // Ping with a simple AT command
            val bytes = byteArrayOf(0x41, 0x54, 0x0D, 0x0A) // AT\r\n
            outputStream.write(bytes)
            outputStream.flush()

            // Wait a bit for response
            delay(500)

            // Check if there's any response
            if (socket.available() > 0) {
                return true
            }

            // Try once more with a carriage return
            outputStream.write(CR.toByteArray())
            outputStream.flush()
            delay(500)

            return socket.available() > 0
        } catch (e: IOException) {
            Log.e(TAG, "Connection validation failed: ${e.message}")
            isConnected = false
            return false
        }
    }

    suspend fun sendCommand(command: String): Boolean {
        return withContext(Dispatchers.IO) {
            var retries = 0
            var success = false

            while (retries < MAX_RETRIES && !success) {
                try {
                    if (!isConnected) {
                        Log.e(TAG, "Cannot send command - not connected")
                        return@withContext false
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

    fun listenForResponses(): Flow<OBD2Response> {
        return flow {
            if (!isConnected) {
                Log.e(TAG, "Cannot listen for responses - not connected")
                return@flow
            }

            val buffer = StringBuilder()
            val responseBuffer = ByteArray(1024)

            try {
                while (isConnected) {
                    val available = socket.available()
                    if (available <= 0) {
                        delay(100) // Avoid tight polling loop
                        continue
                    }

                    val byteCount =
                        try {
                            val count = socket.read(responseBuffer)
                            Log.d(TAG, "Read $count bytes from socket")
                            count
                        } catch (e: IOException) {
                            Log.e(TAG, "Error reading from socket: ${e.message}")
                            isConnected = false
                            throw TransferFailedException()
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

                        if (completeResponse.isNotEmpty()) {
                            Log.d(
                                TAG,
                                "Complete response for $lastCommand: $completeResponse"
                            )

                            // Decode the OBD2 response
                            val decodedResponse =
                                OBD2Decoder.decodeResponse(
                                    lastCommand,
                                    completeResponse
                                )
                            emit(decodedResponse)
                        }

                        buffer.delete(0, endIndex + 1)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in response listener: ${e.message}")
                isConnected = false
                e.printStackTrace()
            }
        }
            .flowOn(Dispatchers.IO)
    }

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
                    Log.d(TAG, "Sending reset command ATZ")
                    success = sendCommand("ATZ")
                    if (!success) {
                        Log.e(TAG, "ATZ command failed")
                        return@withContext false
                    }
                    delay(2000) // ELM327 needs time to reset

                    // Turn off echo
                    Log.d(TAG, "Turning off echo ATE0")
                    success = sendCommand("ATE0")
                    if (!success) {
                        Log.e(TAG, "ATE0 command failed")
                        return@withContext false
                    }
                    delay(300)

                    // Turn off line feeds
                    Log.d(TAG, "Turning off linefeeds ATL0")
                    success = sendCommand("ATL0")
                    if (!success) {
                        Log.e(TAG, "ATL0 command failed")
                        return@withContext false
                    }
                    delay(300)

                    // Turn off headers
                    Log.d(TAG, "Turning off headers ATH0")
                    success = sendCommand("ATH0")
                    if (!success) {
                        Log.e(TAG, "ATH0 command failed")
                        return@withContext false
                    }
                    delay(300)

                    // Set protocol to auto
                    Log.d(TAG, "Setting protocol to auto ATSP0")
                    success = sendCommand("ATSP0")
                    if (!success) {
                        Log.e(TAG, "ATSP0 command failed")
                        return@withContext false
                    }

                    Log.d(TAG, "ELM327 initialization complete")
                    success
                } catch (e: Exception) {
                    Log.e(TAG, "Initialization exception: ${e.message}")
                    e.printStackTrace()
                    isConnected = false
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Outer initialization exception: ${e.message}")
                e.printStackTrace()
                isConnected = false
                false
            }
        }
    }
}
