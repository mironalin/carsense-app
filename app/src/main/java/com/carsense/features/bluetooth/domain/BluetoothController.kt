package com.carsense.features.bluetooth.domain

import com.carsense.features.obd2.domain.OBD2Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BluetoothController {
    val isConnected: StateFlow<Boolean>
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val errors: SharedFlow<String>

    fun startDiscovery()
    fun stopDiscovery()

    fun connectToDevice(device: BluetoothDeviceDomain): Flow<ConnectionResult>

    /**
     * Sends an OBD2 command to the connected device
     * @param command the OBD2 command to send
     * @return an OBD2Message representing the sent command or null if failed
     */
    suspend fun sendOBD2Command(command: String): OBD2Message?

    /**
     * Initializes the OBD2 device communication. This is automatically called internally during the
     * connection flow, so calling it separately is usually not needed.
     * @return true if initialization was successful, false otherwise
     */
    suspend fun initializeOBD2(): Boolean

    fun closeConnection()
    fun release()
}