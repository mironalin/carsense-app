package com.carsense.features.obd2.domain.model

/**
 * A sealed class representing a response that can either be a success with a value or an error with
 * a message.
 * @param T The type of data in case of success
 */
sealed class ValueResponse<out T> {
    /**
     * Represents a successful response with a value
     * @param data The value returned in the response
     */
    data class Success<T>(val data: T) : ValueResponse<T>()

    /**
     * Represents an error response with an error message
     * @param message The error message describing what went wrong
     */
    data class Error(val message: String) : ValueResponse<Nothing>()

    /** Indicates whether this response is an error */
    val isError: Boolean
        get() = this is Error

    /**
     * Returns the value if this is a Success response, throws an exception otherwise
     * @throws IllegalStateException if this is an Error response
     */
    val value: T
        get() =
            when (this) {
                is Success -> data
                is Error ->
                    throw IllegalStateException(
                        "Cannot get value from Error response: $message"
                    )
            }
}
