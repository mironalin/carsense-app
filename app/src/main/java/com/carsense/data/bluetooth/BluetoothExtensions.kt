package com.carsense.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import com.carsense.domain.bluetooth.BluetoothDeviceDomain

/** Extension function to convert Android BluetoothDevice to domain model BluetoothDeviceDomain */
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain {
    return BluetoothDeviceDomain(name = name ?: "Unknown Device", address = address)
}
