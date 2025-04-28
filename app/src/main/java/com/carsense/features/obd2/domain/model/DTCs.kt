package com.carsense.features.obd2.domain.model

/**
 * Represents a collection of Diagnostic Trouble Codes (DTCs) from a vehicle.
 *
 * @property codes The list of DTC codes (e.g., "P0301", "P0420", etc.)
 */
data class DTCs(val codes: List<String>)
