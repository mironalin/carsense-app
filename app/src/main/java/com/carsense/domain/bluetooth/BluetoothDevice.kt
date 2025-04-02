package com.carsense.domain.bluetooth

data class BluetoothDevice(
    val name: String,
    val address: String
)

typealias BluetoothDeviceDomain = BluetoothDevice
