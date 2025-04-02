package com.carsense.core.error

/** Base class for all application errors */
sealed class AppError(override val message: String, override val cause: Throwable? = null) :
    Exception(message, cause) {

    /** Error related to Bluetooth connection issues */
    class ConnectionError(message: String = "Connection error", cause: Throwable? = null) :
        AppError(message, cause)

    /** Error related to timeout of operations */
    class TimeoutError(message: String = "Operation timed out", cause: Throwable? = null) :
        AppError(message, cause)

    /** Error related to parsing data */
    class ParseError(message: String = "Failed to parse data", cause: Throwable? = null) :
        AppError(message, cause)

    /** Error related to command execution */
    class CommandError(message: String = "Command execution failed", cause: Throwable? = null) :
        AppError(message, cause)

    /** Error related to OBD2 protocol */
    class OBD2Error(message: String = "OBD2 protocol error", cause: Throwable? = null) :
        AppError(message, cause)

    /** General error when a specific type cannot be determined */
    class UnknownError(message: String = "Unknown error occurred", cause: Throwable? = null) :
        AppError(message, cause)
}
