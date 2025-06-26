package com.carsense.features.sensors.presentation.screen.util

/**
 * Helper class to track sensor value history for dynamic range calculation
 */
data class SensorValueHistory(
    val values: List<Float> = emptyList(),
    val minObserved: Float = Float.MAX_VALUE,
    val maxObserved: Float = Float.MIN_VALUE
) {
    fun addValue(value: Float): SensorValueHistory {
        val newValues = (values + value).takeLast(20) // Keep last 20 readings for faster response
        return copy(
            values = newValues,
            minObserved = minOf(minObserved, value),
            maxObserved = maxOf(maxObserved, value)
        )
    }

    fun getDynamicRange(defaultMin: Float, defaultMax: Float): Pair<Float, Float> {
        if (values.isEmpty()) return defaultMin to defaultMax

        // For most sensors, keep lower bound at 0 unless values go negative
        // Only timing advance and temperature sensors can have negative default mins
        val shouldAdjustMin = defaultMin < 0f || minObserved < 0f

        val dynamicMin = if (shouldAdjustMin) {
            // For sensors that can go negative, use observed min with padding
            val padding = maxOf((maxObserved - minObserved) * 0.1f, 2f)
            maxOf(defaultMin, minObserved - padding)
        } else {
            // For most sensors, keep minimum at 0
            0f
        }

        // Always adjust upper limit based on observed values
        val padding = maxOf((maxObserved - minObserved) * 0.1f, 2f)
        val dynamicMax = maxObserved + padding

        // Ensure minimum range for readability
        val minRange = when {
            defaultMax <= 100f -> 10f  // For percentages and small values
            defaultMax <= 1000f -> 50f // For medium values
            else -> 500f               // For large values like RPM
        }

        val finalMax = if (dynamicMax - dynamicMin < minRange) {
            dynamicMin + minRange
        } else {
            dynamicMax
        }

        return dynamicMin to finalMax
    }
} 