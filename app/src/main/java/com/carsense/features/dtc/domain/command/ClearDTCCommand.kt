package com.carsense.features.dtc.domain.command

import com.carsense.core.constants.OBD2Constants
import com.carsense.features.obd2.domain.command.OBD2Command

/**
 * Command to clear Diagnostic Trouble Codes (DTCs) from the vehicle. Uses Mode 04
 * (MODE_CLEAR_TROUBLE_CODES) which clears stored DTCs and turns off the MIL (Check Engine Light).
 */
class ClearDTCCommand : OBD2Command() {
    override val mode: Int = OBD2Constants.MODE_CLEAR_TROUBLE_CODES
    override val pid: String = "" // Mode 04 doesn't require a PID
    override val displayName: String = "Clear Trouble Codes"
    override val displayDescription: String =
        "Clears all diagnostic trouble codes and turns off the MIL (Check Engine Light)"
    override val minValue: Float = 0f
    override val maxValue: Float = 0f // Not applicable for clear command
    override val unit: String = ""

    override fun getCommand(): String {
        // For clearing DTCs, we just send the mode number
        return "04"
    }

    override fun parseRawValue(cleanedResponse: String): String {
        // For clear DTCs, any response that's not an error is a success
        // Typical response is "44" (which is 0x40 + mode 4)
        return if (cleanedResponse.contains("44") || cleanedResponse.contains("OK")) {
            "DTCs Cleared Successfully"
        } else {
            "Failed to clear DTCs"
        }
    }
}
