package com.carsense.features.bluetooth.domain

data class BluetoothDevice(
    val name: String,
    val address: String
)
typealias BluetoothDeviceDomain = BluetoothDevice
