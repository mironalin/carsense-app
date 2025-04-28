package com.carsense.features.sensors.domain.command

import com.carsense.features.obd2.domain.command.OBD2Command

/**
 * Base class for all sensor commands. Extends OBD2Command to maintain compatibility with
 * OBD2Decoder. This allows sensor-specific functionality to be organized in the sensors package
 * while maintaining compatibility with the OBD2 system.
 */
abstract class SensorCommand : OBD2Command()
