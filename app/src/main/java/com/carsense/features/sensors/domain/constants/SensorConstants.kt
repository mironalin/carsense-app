package com.carsense.features.sensors.domain.constants

import com.carsense.core.constants.OBD2Constants as CoreOBD2Constants

/** Constants related to vehicle sensors */
object SensorConstants {
    // OBD Modes
    const val MODE_CURRENT_DATA = CoreOBD2Constants.MODE_CURRENT_DATA

    // Timeouts
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val SHORT_TIMEOUT_MS = 2000L

    // Sensor PIDs
    object PID {
        // Engine parameters
        const val ENGINE_LOAD = CoreOBD2Constants.PID.ENGINE_LOAD
        const val COOLANT_TEMP = CoreOBD2Constants.PID.ENGINE_COOLANT_TEMP
        const val ENGINE_RPM = CoreOBD2Constants.PID.ENGINE_RPM
        const val VEHICLE_SPEED = CoreOBD2Constants.PID.VEHICLE_SPEED
        const val INTAKE_AIR_TEMP = CoreOBD2Constants.PID.INTAKE_AIR_TEMP
        const val MAF_RATE = CoreOBD2Constants.PID.MAF_RATE
        const val THROTTLE_POSITION = CoreOBD2Constants.PID.THROTTLE_POSITION
        const val FUEL_LEVEL = CoreOBD2Constants.PID.FUEL_LEVEL
        const val FUEL_PRESSURE = CoreOBD2Constants.PID.FUEL_PRESSURE
    }

    // Parameter ranges
    object Range {
        val RPM = CoreOBD2Constants.Range.RPM
        val SPEED = CoreOBD2Constants.Range.SPEED_KMH
        val TEMPERATURE = CoreOBD2Constants.Range.TEMPERATURE_C
        val PERCENT = CoreOBD2Constants.Range.PERCENT
    }
}
