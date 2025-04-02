package com.carsense.features.obd2.domain.constants

import com.carsense.core.constants.OBD2Constants as CoreOBD2Constants

/**
 * Constants related to OBD-II communication This extends the core OBD2Constants with additional
 * feature-specific constants
 */
object OBD2Constants {
    // OBD Modes (from core)
    const val MODE_CURRENT_DATA = CoreOBD2Constants.MODE_CURRENT_DATA
    const val MODE_FREEZE_FRAME_DATA = CoreOBD2Constants.MODE_FREEZE_FRAME
    const val MODE_STORED_TROUBLE_CODES = CoreOBD2Constants.MODE_TROUBLE_CODES
    const val MODE_CLEAR_TROUBLE_CODES = CoreOBD2Constants.MODE_CLEAR_TROUBLE_CODES
    const val MODE_TEST_RESULTS_O2 = CoreOBD2Constants.MODE_TEST_RESULTS
    const val MODE_TEST_RESULTS_MONITOR = CoreOBD2Constants.MODE_CONTROL
    const val MODE_PENDING_TROUBLE_CODES = CoreOBD2Constants.MODE_PENDING_TROUBLE_CODES
    const val MODE_CONTROL_OPERATION = CoreOBD2Constants.MODE_SPECIAL_CONTROL
    const val MODE_VEHICLE_INFO = CoreOBD2Constants.MODE_VEHICLE_INFO
    const val MODE_PERMANENT_TROUBLE_CODES = CoreOBD2Constants.MODE_PERMANENT_TROUBLE_CODES

    // Timeouts (feature-specific, longer than core defaults for better reliability)
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val INITIALIZATION_TIMEOUT_MS = CoreOBD2Constants.CONNECTION_TIMEOUT
    const val SHORT_TIMEOUT_MS = 2000L

    // ELM327 AT Commands
    const val RESET_COMMAND = CoreOBD2Constants.RESET
    const val ECHO_OFF_COMMAND = CoreOBD2Constants.ECHO_OFF
    const val HEADER_ON_COMMAND = "ATH1" // Core has HEADERS_OFF, we need ON
    const val PROTOCOL_AUTO_COMMAND = CoreOBD2Constants.AUTO_PROTOCOL
    const val SPACES_OFF_COMMAND = "ATS0"
    const val LINEFEED_OFF_COMMAND = CoreOBD2Constants.LINEFEEDS_OFF
    const val TIMEOUT_COMMAND = "ATST"
    const val LONG_MESSAGES_COMMAND = "ATAL"

    // OBD2 PID Categories - using core constants
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
        const val DISTANCE_WITH_MIL = CoreOBD2Constants.PID.DISTANCE_WITH_MIL
        const val FUEL_PRESSURE = CoreOBD2Constants.PID.FUEL_PRESSURE

        // Oxygen sensor related
        const val O2_SENSORS = CoreOBD2Constants.PID.OXYGEN_SENSORS_PRESENT

        // DTC related
        const val DTC_COUNT = CoreOBD2Constants.PID.MONITOR_STATUS

        // Vehicle information
        const val VIN = "02" // Vehicle Identification Number
    }

    // Parameter ranges - using core ranges
    object Range {
        val RPM = CoreOBD2Constants.Range.RPM
        val SPEED = CoreOBD2Constants.Range.SPEED_KMH
        val TEMPERATURE = CoreOBD2Constants.Range.TEMPERATURE_C
        val PERCENT = CoreOBD2Constants.Range.PERCENT
    }
}
