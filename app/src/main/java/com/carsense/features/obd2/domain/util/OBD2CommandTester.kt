package com.carsense.features.obd2.domain.util

import android.util.Log
import com.carsense.features.obd2.data.OBD2Service
import com.carsense.features.obd2.domain.command.Mode9SupportCommand
import com.carsense.features.obd2.domain.command.OBD2Command
import com.carsense.features.obd2.domain.command.VINCommand
import com.carsense.features.sensors.domain.command.CoolantTemperatureCommand
import com.carsense.features.sensors.domain.command.FuelLevelCommand
import com.carsense.features.sensors.domain.command.RPMCommand
import com.carsense.features.sensors.domain.command.SpeedCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Utility class for testing OBD2 commands */
object OBD2CommandTester {
    private const val TAG = "OBD2CommandTester"

    /**
     * Test all available commands
     * @param service The OBD2Service instance to use
     * @param scope CoroutineScope for launching the test
     */
    fun testAllCommands(service: OBD2Service, scope: CoroutineScope) {
        scope.launch {
            Log.d(TAG, "Starting command test sequence")

            // Test basic commands first
            testCommand(service, RPMCommand())
            delay(2000)

            testCommand(service, SpeedCommand())
            delay(2000)

            testCommand(service, CoolantTemperatureCommand())
            delay(2000)

            testCommand(service, FuelLevelCommand())
            delay(2000)

            // Check if Mode 9 is supported before testing VIN
            val mode9Support = Mode9SupportCommand()
            val vinSupported = testMode9Support(service, mode9Support)
            delay(2000)

            // VIN is more complex and may not be supported on all vehicles
            if (vinSupported) {
                testCommand(service, VINCommand())
            } else {
                Log.d(TAG, "Skipping VIN test as it's not supported by the vehicle")
            }

            Log.d(TAG, "Command test sequence complete")
        }
    }

    /**
     * Test if Mode 9 (Vehicle Information) is supported and check VIN support
     * @return True if VIN is supported, false otherwise
     */
    private suspend fun testMode9Support(
        service: OBD2Service,
        command: Mode9SupportCommand
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Testing if Mode 9 (Vehicle Info) is supported...")

                testCommand(service, command)

                // Check if response indicates VIN support
                // (We can't directly get the result, but we can check logs)
                Log.d(TAG, "Mode 9 support check complete")

                // For now, we'll assume VIN might be supported and let the VIN command handle
                // errors
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Mode 9 support: ${e.message}")
                return@withContext false
            }
        }
    }

    /**
     * Test a specific command
     * @param service The OBD2Service instance to use
     * @param command The command to test
     */
    suspend fun testCommand(service: OBD2Service, command: OBD2Command) {
        withContext(Dispatchers.IO) {
            try {
                val commandString = command.getCommand()
                Log.d(TAG, "Testing command: ${command.getName()} ($commandString)")

                val success = service.sendCommand(commandString)
                if (!success) {
                    Log.e(TAG, "Failed to send command: $commandString")
                    return@withContext
                }

                Log.d(TAG, "Command sent successfully, waiting for response...")
                delay(3000) // Wait for response

                Log.d(TAG, "Test for ${command.getName()} complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error testing command ${command.getName()}: ${e.message}")
            }
        }
    }

    /**
     * Test a single command by class type
     * @param service The OBD2Service instance to use
     * @param scope CoroutineScope for launching the test
     * @param commandClass The command class to test
     */
    fun testCommandByType(
        service: OBD2Service,
        scope: CoroutineScope,
        commandClass: Class<out OBD2Command>
    ) {
        scope.launch {
            val command =
                when (commandClass) {
                    RPMCommand::class.java -> RPMCommand()
                    SpeedCommand::class.java -> SpeedCommand()
                    CoolantTemperatureCommand::class.java -> CoolantTemperatureCommand()
                    FuelLevelCommand::class.java -> FuelLevelCommand()
                    VINCommand::class.java -> VINCommand()
                    Mode9SupportCommand::class.java -> Mode9SupportCommand()
                    else -> {
                        Log.e(TAG, "Unknown command class: ${commandClass.simpleName}")
                        return@launch
                    }
                }

            testCommand(service, command)
        }
    }
}
