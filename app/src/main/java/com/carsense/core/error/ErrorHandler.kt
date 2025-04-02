package com.carsense.core.error

import java.io.IOException
import java.util.concurrent.TimeoutException

/** Utility class for handling errors and mapping them to AppError types */
object ErrorHandler {

    /** Maps a generic Throwable to an appropriate AppError */
    fun handle(throwable: Throwable): AppError {
        return when (throwable) {
            is AppError -> throwable
            is TimeoutException -> AppError.TimeoutError(cause = throwable)
            is IOException -> AppError.ConnectionError(cause = throwable)
            else -> AppError.UnknownError(throwable.message ?: "Unknown error", throwable)
        }
    }

    /** Maps Bluetooth-specific errors */
    fun handleBluetoothError(throwable: Throwable): AppError {
        return when (throwable) {
            is TimeoutException -> AppError.TimeoutError("Bluetooth operation timed out", throwable)
            is IOException -> AppError.ConnectionError("Bluetooth connection error", throwable)
            else -> AppError.ConnectionError("Bluetooth error: ${throwable.message}", throwable)
        }
    }

    /** Maps OBD2-specific errors */
    fun handleOBD2Error(throwable: Throwable): AppError {
        return when (throwable) {
            is TimeoutException -> AppError.TimeoutError("OBD2 command timed out", throwable)
            is IOException -> AppError.ConnectionError("OBD2 connection error", throwable)
            else -> AppError.OBD2Error("OBD2 error: ${throwable.message}", throwable)
        }
    }

    /** Maps OBD2 response errors based on error codes */
    fun handleOBD2Response(response: String): AppError? {
        return when {
            response.contains("NO DATA") -> AppError.OBD2Error("No data received from vehicle")
            response.contains("ERROR") -> AppError.OBD2Error("General error in OBD command")
            response.contains("UNABLE TO CONNECT") ->
                    AppError.ConnectionError("Unable to connect to vehicle ECU")
            response.contains("BUS INIT") ->
                    AppError.ConnectionError("Failed to initialize OBD bus")
            response.contains("BUS BUSY") -> AppError.OBD2Error("OBD bus is busy")
            response.contains("FB ERROR") -> AppError.OBD2Error("Feedback error")
            response.contains("DATA ERROR") -> AppError.ParseError("Data error in OBD response")
            response.contains("BUFFER FULL") -> AppError.OBD2Error("Command buffer is full")
            response.contains("CAN ERROR") -> AppError.OBD2Error("CAN protocol error")
            response.contains("ACT ALERT") -> AppError.OBD2Error("Activity monitor alert")
            response.contains("SEARCHING") -> null // Not an error, just a status message
            response.trim().isEmpty() -> AppError.OBD2Error("Empty response from adapter")
            else -> null // Not an error
        }
    }
}
