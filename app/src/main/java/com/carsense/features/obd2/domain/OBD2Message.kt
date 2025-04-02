package com.carsense.features.obd2.domain

/** Represents a message in the OBD2 communication flow (either command or response) */
data class OBD2Message(
    val content: String,
    val type: OBD2MessageType,
    val isFromLocalUser: Boolean
) {
    companion object {
        /** Create a command message from the local device */
        fun createCommand(command: String): OBD2Message {
            return OBD2Message(
                content = command,
                type = OBD2MessageType.COMMAND,
                isFromLocalUser = true
            )
        }

        /** Create a response message from the OBD2 device */
        fun createResponse(response: String, isError: Boolean = false): OBD2Message {
            return OBD2Message(
                content = response,
                type = if (isError) OBD2MessageType.ERROR else OBD2MessageType.RESPONSE,
                isFromLocalUser = false
            )
        }
    }
}

/** Type of OBD2 message */
enum class OBD2MessageType {
    COMMAND, // Command sent to the OBD2 device
    RESPONSE, // Response from the OBD2 device
    ERROR // Error response
}
