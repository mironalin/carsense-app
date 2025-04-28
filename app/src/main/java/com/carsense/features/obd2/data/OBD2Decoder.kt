package com.carsense.features.obd2.data

import com.carsense.core.extensions.containsOBD2Error
import com.carsense.features.obd2.domain.command.Mode9SupportCommand
import com.carsense.features.obd2.domain.command.OBD2Command
import com.carsense.features.obd2.domain.command.VINCommand
import com.carsense.features.sensors.domain.command.CoolantTemperatureCommand
import com.carsense.features.sensors.domain.command.FuelLevelCommand
import com.carsense.features.sensors.domain.command.RPMCommand
import com.carsense.features.sensors.domain.command.SpeedCommand
import kotlin.reflect.KClass

/** Decodes OBD2 responses from the adapter */
object OBD2Decoder {
    // Registry of commands for decoding
    private val commandRegistry =
        mapOf<String, OBD2Command>(
            // Register commands with their raw command strings as keys
            SpeedCommand().getCommand() to SpeedCommand(),
            RPMCommand().getCommand() to RPMCommand(),
            CoolantTemperatureCommand().getCommand() to CoolantTemperatureCommand(),
            FuelLevelCommand().getCommand() to FuelLevelCommand(),
            VINCommand().getCommand() to VINCommand(),
            Mode9SupportCommand().getCommand() to Mode9SupportCommand()
            // Add more commands as they are implemented
        )

    // Registry by command class
    private val commandClassRegistry =
        mapOf<KClass<out OBD2Command>, OBD2Command>(
            SpeedCommand::class to SpeedCommand(),
            RPMCommand::class to RPMCommand(),
            CoolantTemperatureCommand::class to CoolantTemperatureCommand(),
            FuelLevelCommand::class to FuelLevelCommand(),
            VINCommand::class to VINCommand(),
            Mode9SupportCommand::class to Mode9SupportCommand()
            // Add more commands as they are implemented
        )

    /** Decodes a raw OBD2 response into a structured OBD2Response */
    fun decodeResponse(command: String, response: String): OBD2Response {
        // Check for errors or "NO DATA" responses
        if (response.containsOBD2Error()) {
            return OBD2Response.createError(command, "Error in OBD response", response)
        }

        // Check for AT command responses (non-OBD commands)
        if (command.startsWith("AT", ignoreCase = true)) {
            return OBD2Response(
                command = command,
                rawData = response,
                decodedValue = response,
                unit = "",
                isError = false
            )
        }

        // Find the command object that can handle this command
        val obd2Command = commandRegistry[command.uppercase()]

        // If we have a registered command, use it to parse the response
        if (obd2Command != null) {
            try {
                val sensorReading = obd2Command.parseResponse(response)
                return OBD2Response(
                    command = command,
                    rawData = response,
                    decodedValue = sensorReading.value,
                    unit = sensorReading.unit,
                    isError = sensorReading.isError,
                    timestamp = sensorReading.timestamp
                )
            } catch (e: Exception) {
                return OBD2Response.createError(
                    command,
                    "Error parsing response: ${e.message}",
                    response
                )
            }
        }

        // Fallback for unregistered commands: return raw data
        return OBD2Response(
            command = command,
            rawData = response,
            decodedValue = "Raw: $response",
            unit = "",
            isError = false
        )
    }

    /** Decode a response using a specific command class */
    fun <T : OBD2Command> decodeWithCommand(
        commandClass: KClass<T>,
        response: String
    ): OBD2Response {
        val command =
            commandClassRegistry[commandClass]
                ?: return OBD2Response.createError(
                    "",
                    "Unknown command class: ${commandClass.simpleName}"
                )

        try {
            val sensorReading = command.parseResponse(response)
            return OBD2Response.fromSensorReading(sensorReading, command.getCommand())
        } catch (e: Exception) {
            return OBD2Response.createError(
                command.getCommand(),
                "Error parsing with ${commandClass.simpleName}: ${e.message}",
                response
            )
        }
    }

    /** Get a command object by its class */
    fun <T : OBD2Command> getCommand(commandClass: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST") return commandClassRegistry[commandClass] as? T
    }
}
