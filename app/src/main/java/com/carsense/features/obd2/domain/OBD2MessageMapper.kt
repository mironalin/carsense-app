package com.carsense.features.obd2.domain

import com.carsense.features.obd2.data.OBD2Response
import com.carsense.features.obd2.presentation.model.MessageModel

/** Utility class to help with OBD2Message and OBD2Response display */
object OBD2MessageMapper {
    /** Convert an OBD2Response to a UI-friendly display model */
    fun convertToBluetoothMessage(response: OBD2Response): MessageModel {
        val sender = when {
            response.isError -> "Error"
            response.command.uppercase().startsWith("AT") -> "Adapter"
            else -> "Decoded"
        }
        // For AT commands, the rawData might be more informative than decodedValue if decodedValue is just the same.
        // For OBD commands, decodedValue is preferred.
        val displayContent = if (response.command.uppercase()
                .startsWith("AT") && response.decodedValue == response.rawData
        ) {
            response.rawData // Show raw AT response if decoded is same as raw (e.g. "OK")
        } else {
            if (response.unit.isNotBlank()) {
                "${response.decodedValue} ${response.unit}" // Add unit if available
            } else {
                response.decodedValue
            }
        }

        return MessageModel(
            message = "${response.command}: $displayContent", // Prepend command for context
            // Consider adding a field to MessageModel for sender if needed, or bake into message string
        )
    }

    // Keep the old method for OBD2Message if it's still used elsewhere, e.g. by ConnectionResult
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
