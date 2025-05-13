package com.carsense.features.obd2.data

import com.carsense.core.extensions.containsOBD2Error
import com.carsense.features.obd2.data.OBD2Decoder.commandClassRegistry
import com.carsense.features.obd2.data.OBD2Decoder.commandRegistry
import com.carsense.features.obd2.data.OBD2Decoder.decodeResponse
import com.carsense.features.obd2.data.OBD2Decoder.decodeWithCommand
import com.carsense.features.obd2.data.OBD2Decoder.getCommand
import com.carsense.features.obd2.domain.command.Mode9SupportCommand
import com.carsense.features.obd2.domain.command.OBD2Command
import com.carsense.features.obd2.domain.command.VINCommand
import com.carsense.features.sensors.domain.command.CoolantTemperatureCommand
import com.carsense.features.sensors.domain.command.EngineLoadCommand
import com.carsense.features.sensors.domain.command.FuelLevelCommand
import com.carsense.features.sensors.domain.command.IntakeAirTemperatureCommand
import com.carsense.features.sensors.domain.command.IntakeManifoldPressureCommand
import com.carsense.features.sensors.domain.command.MassAirFlowCommand
import com.carsense.features.sensors.domain.command.RPMCommand
import com.carsense.features.sensors.domain.command.SpeedCommand
import com.carsense.features.sensors.domain.command.ThrottlePositionCommand
import com.carsense.features.sensors.domain.command.TimingAdvanceCommand
import kotlin.reflect.KClass

/**
 * Singleton object responsible for decoding raw OBD-II responses into structured [OBD2Response]
 * objects.
 *
 * It maintains registries of known [OBD2Command] instances:
 * - [commandRegistry]: Maps raw command strings (e.g., "010C") to their corresponding [OBD2Command]
 * object.
 * - [commandClassRegistry]: Maps [KClass] of an [OBD2Command] to its instance.
 *
 * The primary method, [decodeResponse], attempts to find a registered command handler based on the
 * original command string and uses it to parse the raw response. It also handles generic error
 * conditions and AT command responses.
 *
 * The [decodeWithCommand] method allows decoding a response using a specific command class, and
 * [getCommand] retrieves a command instance by its class.
 */
object OBD2Decoder {
    // Registry mapping raw OBD-II command strings (e.g., "010C") to their corresponding
    // [OBD2Command] instances. This allows [decodeResponse] to find the correct parser
    // for a given command string.
    // Commands are registered here upon object initialization.
    //
    // This registry is initialized with a list of commands that are known to be supported by the
    // vehicle. This is done in the [SensorRepositoryImpl] class.
    //
    // This registry is used to decode the response from the OBD2 adapter.
    private val commandRegistry =
        mapOf<String, OBD2Command>(
            // Register commands with their raw command strings as keys
            SpeedCommand().getCommand() to SpeedCommand(),
            RPMCommand().getCommand() to RPMCommand(),
            CoolantTemperatureCommand().getCommand() to CoolantTemperatureCommand(),
            FuelLevelCommand().getCommand() to FuelLevelCommand(),
            IntakeAirTemperatureCommand().getCommand() to IntakeAirTemperatureCommand(),
            ThrottlePositionCommand().getCommand() to ThrottlePositionCommand(),
            EngineLoadCommand().getCommand() to EngineLoadCommand(),
            TimingAdvanceCommand().getCommand() to TimingAdvanceCommand(),
            MassAirFlowCommand().getCommand() to MassAirFlowCommand(),
            IntakeManifoldPressureCommand().getCommand() to IntakeManifoldPressureCommand(),
            VINCommand().getCommand() to VINCommand(),
            Mode9SupportCommand().getCommand() to Mode9SupportCommand()
            // Add more commands as they are implemented
        )

    // Registry mapping [KClass] objects of [OBD2Command] subclasses to their singleton instances.
    // This allows for type-safe retrieval and decoding when the specific command class is known
    // (e.g., via [decodeWithCommand] or [getCommand]).
    // Commands are registered here upon object initialization.
    //
    // This registry is initialized with a list of commands that are known to be supported by the
    // vehicle. This is done in the [SensorRepositoryImpl] class.
    //
    // This registry is used to decode the response from the OBD2 adapter.
    private val commandClassRegistry =
        mapOf<KClass<out OBD2Command>, OBD2Command>(
            SpeedCommand::class to SpeedCommand(),
            RPMCommand::class to RPMCommand(),
            CoolantTemperatureCommand::class to CoolantTemperatureCommand(),
            FuelLevelCommand::class to FuelLevelCommand(),
            IntakeAirTemperatureCommand::class to IntakeAirTemperatureCommand(),
            ThrottlePositionCommand::class to ThrottlePositionCommand(),
            EngineLoadCommand::class to EngineLoadCommand(),
            TimingAdvanceCommand::class to TimingAdvanceCommand(),
            MassAirFlowCommand::class to MassAirFlowCommand(),
            IntakeManifoldPressureCommand::class to IntakeManifoldPressureCommand(),
            VINCommand::class to VINCommand(),
            Mode9SupportCommand::class to Mode9SupportCommand()
            // Add more commands as they are implemented
        )

    /**
     * Decodes a raw OBD-II string response into a structured [OBD2Response].
     *
     * The decoding process is as follows:
     * 1. Checks if the `response` string contains known OBD-II error substrings (e.g., "NO DATA", "ERROR").
     *    If so, returns an error [OBD2Response].
     * 2. Checks if the original `command` string starts with "AT" (case-insensitive), indicating an
     *    AT (Attention) command. If so, it assumes the response is a direct textual reply from the
     *    adapter and returns an [OBD2Response] with the raw response as the decoded value.
     * 3. Attempts to find an [OBD2Command] instance in the [commandRegistry] using the uppercase
     *    version of the `command` string as the key.
     * 4. If a matching [OBD2Command] is found:
     *    a. It calls `obd2Command.parseResponse(response)` to get a [SensorReading].
     *    b. Constructs an [OBD2Response] from the [SensorReading]'s properties (value, unit, error status, timestamp).
     *    c. If `parseResponse` throws an exception, it catches it and returns an error [OBD2Response].
     * 5. If no matching [OBD2Command] is found in the registry (i.e., it's an unknown OBD-II command),
     *    it returns an [OBD2Response] where the `decodedValue` is simply "Raw: " prepended to the
     *    `response` string, and `isError` is false (as it's not an adapter error, just an unparsable one).
     *
     * @param command The original OBD-II command string that was sent (e.g., "010C", "ATI").
     * @param response The raw string response received from the OBD-II adapter.
     * @return An [OBD2Response] object containing the parsed data or error information.
     */
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

    /**
     * Decodes a raw OBD-II string response using a specific [OBD2Command] class for parsing.
     *
     * This method is useful when the type of command that generated the response is known beforehand.
     * 1. Retrieves an instance of the specified `commandClass` from the [commandClassRegistry].
     *    If the class is not registered, returns an error [OBD2Response].
     * 2. Calls `command.parseResponse(response)` on the retrieved command instance to get a [SensorReading].
     * 3. Constructs an [OBD2Response] from the resulting [SensorReading] using
     *    [OBD2Response.fromSensorReading].
     * 4. If `parseResponse` throws an exception, it catches it and returns an error [OBD2Response],
     *    noting the command class that attempted parsing.
     *
     * @param T The specific subtype of [OBD2Command] to use for parsing.
     * @param commandClass The [KClass] of the [OBD2Command] to use (e.g., `RPMCommand::class`).
     * @param response The raw string response received from the OBD-II adapter.
     * @return An [OBD2Response] object containing the parsed data or error information.
     */
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

    /**
     * Retrieves a pre-registered instance of a specific [OBD2Command] subclass.
     *
     * This allows access to the singleton instances of commands stored in the [commandClassRegistry].
     *
     * @param T The specific subtype of [OBD2Command] to retrieve.
     * @param commandClass The [KClass] of the [OBD2Command] to retrieve (e.g., `RPMCommand::class`).
     * @return The registered instance of the command cast to type `T`, or `null` if the
     *   `commandClass` is not found in the registry. The cast is suppressed as "UNCHECKED_CAST"
     *   because the registry is designed to hold matching instances.
     */
    fun <T : OBD2Command> getCommand(commandClass: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST") return commandClassRegistry[commandClass] as? T
    }
}
