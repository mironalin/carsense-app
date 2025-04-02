package com.carsense.data.obd2

object OBD2Decoder {
    // Standard OBD2 mode 1 PIDs
    private const val ENGINE_RPM = "010C"
    private const val VEHICLE_SPEED = "010D"
    private const val ENGINE_COOLANT_TEMP = "0105"
    private const val THROTTLE_POSITION = "0111"
    private const val MAF_SENSOR = "0110"
    private const val INTAKE_AIR_TEMP = "010F"
    private const val FUEL_LEVEL = "012F"
    private const val DISTANCE_WITH_MIL = "0121"
    private const val MAP_SENSOR = "010B"

    fun decodeResponse(command: String, response: String): OBD2Response {
        // Check for errors or "NO DATA" responses
        if (response.contains("ERROR", ignoreCase = true) ||
            response.contains("NO DATA", ignoreCase = true) ||
            response.contains("UNABLE TO CONNECT", ignoreCase = true)
        ) {
            return OBD2Response(
                command = command,
                rawData = response,
                decodedValue = "Error",
                unit = "",
                isError = true
            )
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

        // For OBD2 responses, remove spaces and filter out non-hex characters
        val hexData =
            response.replace(" ", "")
                .replace("41", "", ignoreCase = true) // Remove mode byte
                .replace(command.substring(2), "", ignoreCase = true) // Remove PID byte
                .filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }

        // Decode based on command type
        return when (command.uppercase()) {
            ENGINE_RPM -> decodeRPM(command, response, hexData)
            VEHICLE_SPEED -> decodeSpeed(command, response, hexData)
            ENGINE_COOLANT_TEMP -> decodeTemperature(command, response, hexData)
            THROTTLE_POSITION -> decodePercentage(command, response, hexData)
            INTAKE_AIR_TEMP -> decodeTemperature(command, response, hexData)
            FUEL_LEVEL -> decodePercentage(command, response, hexData)
            MAF_SENSOR -> decodeMAF(command, response, hexData)
            MAP_SENSOR -> decodeMAP(command, response, hexData)
            else ->
                OBD2Response(
                    command = command,
                    rawData = response,
                    decodedValue = "Raw: $hexData",
                    unit = "",
                    isError = false
                )
        }
    }

    private fun decodeRPM(command: String, response: String, hexData: String): OBD2Response {
        return try {
            val a = hexData.substring(0, 2).toInt(16)
            val b = hexData.substring(2, 4).toInt(16)
            val rpm = (a * 256 + b) / 4.0

            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = rpm.toInt().toString(),
                unit = "RPM",
                isError = false
            )
        } catch (e: Exception) {
            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = "Error parsing",
                unit = "",
                isError = true
            )
        }
    }

    private fun decodeSpeed(command: String, response: String, hexData: String): OBD2Response {
        return try {
            val speed = hexData.toInt(16)

            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = speed.toString(),
                unit = "km/h",
                isError = false
            )
        } catch (e: Exception) {
            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = "Error parsing",
                unit = "",
                isError = true
            )
        }
    }

    private fun decodeTemperature(
        command: String,
        response: String,
        hexData: String
    ): OBD2Response {
        return try {
            val temp = hexData.toInt(16) - 40

            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = temp.toString(),
                unit = "°C",
                isError = false
            )
        } catch (e: Exception) {
            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = "Error parsing",
                unit = "",
                isError = true
            )
        }
    }

    private fun decodePercentage(command: String, response: String, hexData: String): OBD2Response {
        return try {
            val percentage = hexData.toInt(16) * 100 / 255.0

            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = String.format("%.1f", percentage),
                unit = "%",
                isError = false
            )
        } catch (e: Exception) {
            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = "Error parsing",
                unit = "",
                isError = true
            )
        }
    }

    private fun decodeMAF(command: String, response: String, hexData: String): OBD2Response {
        return try {
            val a = hexData.substring(0, 2).toInt(16)
            val b = hexData.substring(2, 4).toInt(16)
            val maf = (a * 256 + b) / 100.0

            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = String.format("%.2f", maf),
                unit = "g/s",
                isError = false
            )
        } catch (e: Exception) {
            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = "Error parsing",
                unit = "",
                isError = true
            )
        }
    }

    private fun decodeMAP(command: String, response: String, hexData: String): OBD2Response {
        return try {
            val pressure = hexData.toInt(16)

            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = pressure.toString(),
                unit = "kPa",
                isError = false
            )
        } catch (e: Exception) {
            OBD2Response(
                command = command,
                rawData = response,
                decodedValue = "Error parsing",
                unit = "",
                isError = true
            )
        }
    }
}
