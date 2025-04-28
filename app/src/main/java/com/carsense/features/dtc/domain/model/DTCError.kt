package com.carsense.features.dtc.domain.model

/**
 * Represents a Diagnostic Trouble Code with its description
 *
 * @property code The DTC code (e.g., "P0301")
 * @property description Human-readable description of what the code means
 */
data class DTCError(val code: String, val description: String)
