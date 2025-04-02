package com.carsense.domain.bluetooth

import com.carsense.domain.obd2.OBD2Message

/** Represents the result of a Bluetooth connection attempt or data transfer operation */
sealed class ConnectionResult {
    /** Indicates that a connection has been established with a device */
    object ConnectionEstablished : ConnectionResult()

    /**
     * Indicates that a message has been successfully transferred from the connected device
     * @param message The OBD2 message received from the device
     */
    data class TransferSucceeded(val message: OBD2Message) : ConnectionResult()

    /**
     * Indicates that an error occurred during connection or data transfer
     * @param message The error message
     */
    data class Error(val message: String) : ConnectionResult()
}
