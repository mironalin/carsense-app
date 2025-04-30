package com.carsense.core.extensions

/** Converts a hex string to an integer */
fun String.hexToInt(): Int {
    return this.trim().toInt(16)
}

/** Converts a hex string to a byte */
fun String.hexToByte(): Byte {
    return this.trim().toInt(16).toByte()
}

/**
 * Parses a standard OBD2 response for mode 01 commands Example input: "41 0C 1A F8" for RPM Returns
 * a list of Integer values from the response, or null if it doesn't match the expected format
 */
fun String.parseOBD2Response(mode: Int, pid: String): List<Int>? {
    val parts = this.trim().split(" ")
    val expectedMode = String.format("%02X", 0x40 + mode)

    // Check response format
    if (parts.size < 3 || parts[0] != expectedMode || parts[1] != pid) {
        return null
    }

    // Convert data bytes to integers
    return parts.drop(2).map { it.hexToInt() }
}

/** Checks if this OBD2 response contains an error */
fun String.containsOBD2Error(): Boolean {
    val upperCase = this.uppercase()
    return upperCase.contains("ERROR") ||
            upperCase.contains("UNABLE TO CONNECT") ||
            upperCase.contains("NO DATA") ||
            upperCase.contains("NODATA") ||
            upperCase.contains("BUS ERROR") ||
            upperCase.contains("CAN ERROR") ||
            upperCase.contains("BUS INIT") ||
            upperCase.contains("DATA ERROR")
}

/** Checks if this response indicates the adapter is still initializing */
fun String.isAdapterInitializing(): Boolean {
    val upperCase = this.uppercase()
    return upperCase.contains("SEARCHING") || upperCase.contains("STOPPED")
}

/**
 * Formats a command for transmission to the OBD adapter Adds carriage return and optional spaces
 */
fun String.formatOBD2Command(addSpaces: Boolean = false): String {
    val command =
        if (addSpaces) {
            this.chunked(2).joinToString(" ")
        } else {
            this
        }
    return "$command\r"
}

/** Builds an OBD2 command string from mode and PID */
fun buildOBD2Command(mode: Int, pid: String): String {
    return String.format("%02X%s", mode, pid)
}
