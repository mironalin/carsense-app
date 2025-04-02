package com.carsense.features.bluetooth.presentation.model

import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain
import com.carsense.features.obd2.presentation.model.MessageDisplay

data class BluetoothState(
    val scannedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val messages: List<MessageDisplay> = emptyList()
)
