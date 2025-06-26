package com.carsense.features.sensors.presentation.screen.util

/**
 * Helper object to map sensor IDs to PID constants
 */
object SensorPidMapping {
    // Import PID constants from SensorViewModel
    const val PID_RPM = "0C"
    const val PID_SPEED = "0D"
    const val PID_COOLANT_TEMP = "05"
    const val PID_INTAKE_AIR_TEMP = "0F"
    const val PID_THROTTLE_POSITION = "11"
    const val PID_FUEL_LEVEL = "2F"
    const val PID_ENGINE_LOAD = "04"
    const val PID_INTAKE_MANIFOLD_PRESSURE = "0B"
    const val PID_TIMING_ADVANCE = "0E"
    const val PID_MAF_RATE = "10"

    fun mapSensorIdToPid(sensorId: String): String {
        return when (sensorId) {
            "rpm" -> PID_RPM
            "speed" -> PID_SPEED
            "coolant" -> PID_COOLANT_TEMP
            "intake" -> PID_INTAKE_AIR_TEMP
            "throttle" -> PID_THROTTLE_POSITION
            "fuel" -> PID_FUEL_LEVEL
            "load" -> PID_ENGINE_LOAD
            "manifold" -> PID_INTAKE_MANIFOLD_PRESSURE
            "timing" -> PID_TIMING_ADVANCE
            "maf" -> PID_MAF_RATE
            else -> sensorId
        }
    }
} 