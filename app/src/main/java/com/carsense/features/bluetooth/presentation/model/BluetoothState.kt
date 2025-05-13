package com.carsense.features.bluetooth.presentation.model

import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain
import com.carsense.features.obd2.presentation.model.MessageModel

/**
 * Represents the current state of the Bluetooth connection.
 *
 * This data class is used to store the current state of the Bluetooth connection. It is used to
 * store the current state of the Bluetooth connection.
 */
data class BluetoothState(
    val scannedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectedDeviceAddress: String? = null,
    val errorMessage: String? = null,
    val messages: List<MessageModel> = emptyList()
)
