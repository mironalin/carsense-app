package com.carsense.features.bluetooth.data

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain

/** Extension function to convert Android BluetoothDevice to domain model BluetoothDeviceDomain */
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    return BluetoothDeviceDomain(name = name ?: "Unknown Device", address = address)
}
