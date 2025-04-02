package com.carsense.domain.chat

data class BluetoothDevice(
    val name: String?,
    val address: String
)
typealias BluetoothDeviceDomain = BluetoothDevice
