package com.carsense.core.command

/**
 * Base interface for the Command pattern implementation.
 * All commands in the application should implement this interface.
 */
interface Command<T> {
    /**
     * Returns the raw string that will be sent to the target system (e.g., OBD adapter)
     */
    fun getCommand(): String

    /**
     * Parses the raw response from the target system and converts it to the appropriate result type
     *
     * @param rawResponse The response received from the target system
     * @return The parsed response of type T
     */
    fun parseResponse(rawResponse: String): T

    /**
     * Human-readable name of the command
     */
    fun getName(): String

    /**
     * Description of what the command does
     */
    fun getDescription(): String
}