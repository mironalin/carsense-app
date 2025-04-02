package com.carsense.features.obd2.domain

import com.carsense.features.obd2.presentation.model.MessageModel

/** Utility class to help with OBD2Message display */
object OBD2MessageMapper {
    /** Convert an OBD2Message to a UI-friendly display model */
    fun convertToBluetoothMessage(message: OBD2Message): MessageModel {
        val sender =
            when (message.type) {
                OBD2MessageType.COMMAND -> "User"
                OBD2MessageType.ERROR -> "Error"
                OBD2MessageType.RESPONSE -> "Decoded"
            }

        return MessageModel(
            message = message.content,
        )
    }
}
