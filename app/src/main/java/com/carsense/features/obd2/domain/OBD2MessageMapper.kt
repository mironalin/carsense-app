package com.carsense.features.obd2.domain

import com.carsense.features.obd2.presentation.model.MessageDisplay

/** Utility class to help with OBD2Message display */
object OBD2MessageMapper {
    /** Convert an OBD2Message to a UI-friendly display model */
    fun convertToBluetoothMessage(message: OBD2Message): MessageDisplay {
        val sender =
            when (message.type) {
                OBD2MessageType.COMMAND -> "User"
                OBD2MessageType.ERROR -> "Error"
                OBD2MessageType.RESPONSE -> "Decoded"
            }

        return MessageDisplay(
            message = message.content,
        )
    }
}
