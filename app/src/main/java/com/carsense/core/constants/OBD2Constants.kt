package com.carsense.core.constants

/** Constants for OBD2 communication and protocols */
object OBD2Constants {
    // ELM327 Commands
    const val RESET = "ATZ"
    const val ECHO_OFF = "ATE0"
    const val HEADERS_OFF = "ATH0"
    const val LINEFEEDS_OFF = "ATL0"
    const val AUTO_PROTOCOL = "ATSP0"
    const val SET_PROTOCOL_1 = "ATSP1" // SAE J1850 PWM
    const val SET_PROTOCOL_2 = "ATSP2" // SAE J1850 VPW
    const val SET_PROTOCOL_3 = "ATSP3" // ISO 9141-2
    const val SET_PROTOCOL_4 = "ATSP4" // ISO 14230-4 KWP (5 baud init)
    const val SET_PROTOCOL_5 = "ATSP5" // ISO 14230-4 KWP (fast init)
    const val SET_PROTOCOL_6 = "ATSP6" // ISO 15765-4 CAN (11 bit ID, 500 kbaud)

    // OBD Modes
    const val MODE_CURRENT_DATA = 1
    const val MODE_FREEZE_FRAME = 2
    const val MODE_TROUBLE_CODES = 3
    const val MODE_CLEAR_TROUBLE_CODES = 4
    const val MODE_TEST_RESULTS = 5
    const val MODE_CONTROL = 6
    const val MODE_PENDING_TROUBLE_CODES = 7
    const val MODE_SPECIAL_CONTROL = 8
    const val MODE_VEHICLE_INFO = 9
    const val MODE_PERMANENT_TROUBLE_CODES = 10

    // Common PIDs for Mode 01
    object PID {
        const val SUPPORTED_PIDS_01_20 = "00"
        const val MONITOR_STATUS = "01"
        const val FREEZE_DTC = "02"
        const val FUEL_SYSTEM_STATUS = "03"
        const val ENGINE_LOAD = "04"
        const val ENGINE_COOLANT_TEMP = "05"
        const val SHORT_TERM_FUEL_TRIM_1 = "06"
        const val LONG_TERM_FUEL_TRIM_1 = "07"
        const val SHORT_TERM_FUEL_TRIM_2 = "08"
        const val LONG_TERM_FUEL_TRIM_2 = "09"
        const val FUEL_PRESSURE = "0A"
        const val INTAKE_MANIFOLD_PRESSURE = "0B"
        const val ENGINE_RPM = "0C"
        const val VEHICLE_SPEED = "0D"
        const val TIMING_ADVANCE = "0E"
        const val INTAKE_AIR_TEMP = "0F"
        const val MAF_RATE = "10"
        const val THROTTLE_POSITION = "11"
        const val SECONDARY_AIR_STATUS = "12"
        const val OXYGEN_SENSORS_PRESENT = "13"
        const val OXYGEN_SENSOR_1 = "14"
        const val OXYGEN_SENSOR_2 = "15"
        const val OXYGEN_SENSOR_3 = "16"
        const val OXYGEN_SENSOR_4 = "17"
        const val OXYGEN_SENSOR_5 = "18"
        const val OXYGEN_SENSOR_6 = "19"
        const val OXYGEN_SENSOR_7 = "1A"
        const val OXYGEN_SENSOR_8 = "1B"
        const val OBD_STANDARD = "1C"
        const val OXYGEN_SENSORS_PRESENT_2 = "1D"
        const val AUX_INPUT_STATUS = "1E"
        const val RUN_TIME = "1F"
        const val SUPPORTED_PIDS_21_40 = "20"
        const val DISTANCE_WITH_MIL = "21"
        const val FUEL_RAIL_PRESSURE = "22"
        const val FUEL_RAIL_GAUGE_PRESSURE = "23"
        const val OXYGEN_SENSOR_1_FUEL_TRIM = "24"
        const val OXYGEN_SENSOR_2_FUEL_TRIM = "25"
        const val OXYGEN_SENSOR_3_FUEL_TRIM = "26"
        const val OXYGEN_SENSOR_4_FUEL_TRIM = "27"
        const val OXYGEN_SENSOR_5_FUEL_TRIM = "28"
        const val OXYGEN_SENSOR_6_FUEL_TRIM = "29"
        const val OXYGEN_SENSOR_7_FUEL_TRIM = "2A"
        const val OXYGEN_SENSOR_8_FUEL_TRIM = "2B"
        const val EGR = "2C"
        const val EGR_ERROR = "2D"
        const val EVAPORATIVE_PURGE = "2E"
        const val FUEL_LEVEL = "2F"
        const val WARM_UPS_SINCE_CODES_CLEARED = "30"
        const val DISTANCE_SINCE_CODES_CLEARED = "31"
        const val EVAP_SYSTEM_VAPOR_PRESSURE = "32"
        const val BAROMETRIC_PRESSURE = "33"
    }

    // Default Timeouts
    const val DEFAULT_TIMEOUT = 250L
    const val INIT_TIMEOUT = 1000L
    const val CONNECTION_TIMEOUT = 10000L

    // Common parameter ranges
    object Range {
        val RPM = 0f..8000f
        val SPEED_KMH = 0f..220f
        val TEMPERATURE_C = -40f..215f
        val PERCENT = 0f..100f
        val VOLTAGE = 0f..20f
        val PRESSURE_KPA = 0f..255f
        val PRESSURE_PSI = 0f..36.25f
        val TORQUE_NM = 0f..500f
    }
}
