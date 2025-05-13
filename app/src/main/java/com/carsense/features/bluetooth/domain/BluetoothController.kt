package com.carsense.features.bluetooth.domain

import com.carsense.features.obd2.data.OBD2BluetoothService
import com.carsense.features.obd2.domain.OBD2Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing Bluetooth device connections and interactions.
 *
 * This interface defines the contract for managing Bluetooth device connections and interactions. It
 * provides methods for starting and stopping Bluetooth device discovery, connecting to a Bluetooth
 * device, and getting the OBD2 Bluetooth service.
 */
interface BluetoothController {
    val isConnected: StateFlow<Boolean>
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
    val errors: SharedFlow<String>

    /**
     * Starts the Bluetooth device dis  covery.
     *
     * This method is used to start the Bluetooth device discovery. It is used to start the
     * Bluetooth device discovery.
     */
    fun startDiscovery()

    /**
     * Stops the Bluetooth device discovery.
     *
     * This method is used to stop the Bluetooth device discovery. It is used to stop the Bluetooth
     * device discovery.
     */
    fun stopDiscovery()

    /**
     * Connects to a Bluetooth device.
     *
     * This method is used to connect to a Bluetooth device. It is used to connect to a Bluetooth
     * device.
     */
    fun connectToDevice(device: BluetoothDeviceDomain): Flow<ConnectionResult>

    /**
     * Gets the OBD2 Bluetooth service.
     *
     * This method is used to get the OBD2 Bluetooth service. It is used to get the OBD2 Bluetooth
     * service.
     */
    fun getObd2Service(): OBD2BluetoothService?

    /**
     * @deprecated Prefer using [getObd2Service] and then [OBD2BluetoothService.executeOBD2Command]
     * or [OBD2BluetoothService.executeAtCommand] for more robust, Flow-based responses.
     */
    @Deprecated(
        "Prefer using getObd2Service() for more direct and Flow-based command execution.",
        ReplaceWith(
            "getObd2Service()?.executeAtCommand(command) or getObd2Service()?.executeOBD2Command(typedCommand)"
        )
    )
    suspend fun sendOBD2Command(command: String): OBD2Message?

    /**
     * Initializes the OBD2 device communication. This is automatically called internally during the
     * connection flow, so calling it separately is usually not needed.
     * @return true if initialization was successful, false otherwise
     */
    suspend fun initializeOBD2(): Boolean

    /**
     * Closes the connection to the OBD2 device.
     *
     * This method is used to close the connection to the OBD2 device. It is used to close the
     * connection to the OBD2 device.
     */
    fun closeConnection()

    /**
     * Releases the Bluetooth controller.
     *
     * This method is used to release the Bluetooth controller. It is used to release the Bluetooth
     * controller.
     */
    fun release()
}
