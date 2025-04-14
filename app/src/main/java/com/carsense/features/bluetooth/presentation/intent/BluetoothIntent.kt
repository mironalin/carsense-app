package com.carsense.features.bluetooth.presentation.intent

import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain


/** Represents all possible user actions (intents) for Bluetooth functionality */
sealed class BluetoothIntent {
    data class ConnectToDevice(val device: BluetoothDeviceDomain) : BluetoothIntent()
    object DisconnectFromDevice : BluetoothIntent()
    object StartScan : BluetoothIntent()
    object StopScan : BluetoothIntent()
    data class SendCommand(val message: String) : BluetoothIntent()

    object DismissError : BluetoothIntent()
}
