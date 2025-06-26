package com.carsense.features.sensors.presentation.screen.util

import androidx.compose.ui.graphics.vector.ImageVector
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.presentation.screen.model.SensorGaugeConfig
import com.carsense.features.sensors.presentation.viewmodel.SensorState
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Thermometer
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Wind

/**
 * Factory object for creating sensor gauge configurations
 */
object SensorConfigFactory {

    // Set of sensors that should never use dynamic ranges
    private val nonDynamicSensors = setOf(
        "throttle", "load", "coolant", "fuel", "timing"
    )

    /**
     * Creates all available sensor configurations with dynamic ranges applied
     */
    fun createAllSensorConfigs(
        state: SensorState,
        lastReadings: Map<String, SensorReading>,
        selectedSensorIds: Set<String>,
        sensorHistory: Map<String, SensorValueHistory>
    ): List<SensorGaugeConfig> {
        return listOf(
            createSensorConfig(
                id = "rpm",
                title = "RPM",
                icon = Lucide.Activity,
                defaultMin = 0f,
                defaultMax = 8000f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "speed",
                title = "Speed",
                icon = Lucide.Gauge,
                defaultMin = 0f,
                defaultMax = 180f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "coolant",
                title = "Coolant Temp",
                icon = Lucide.Thermometer,
                defaultMin = 50f,
                defaultMax = 130f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "intake",
                title = "Intake Air Temp",
                icon = Lucide.Wind,
                defaultMin = 0f,
                defaultMax = 220f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "throttle",
                title = "Throttle Position",
                icon = Lucide.Gauge,
                defaultMin = 0f,
                defaultMax = 100f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "fuel",
                title = "Fuel Level",
                icon = Lucide.Droplet,
                defaultMin = 0f,
                defaultMax = 100f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "load",
                title = "Engine Load",
                icon = Lucide.Activity,
                defaultMin = 0f,
                defaultMax = 100f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "manifold",
                title = "Manifold Pressure",
                icon = Lucide.Gauge,
                defaultMin = 0f,
                defaultMax = 250f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "timing",
                title = "Timing Advance",
                icon = Lucide.Timer,
                defaultMin = -64f,
                defaultMax = 64f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            ),
            createSensorConfig(
                id = "maf",
                title = "Mass Air Flow",
                icon = Lucide.Wind,
                defaultMin = 0f,
                defaultMax = 650f,
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            )
        )
    }

    private fun createSensorConfig(
        id: String,
        title: String,
        icon: ImageVector,
        defaultMin: Float,
        defaultMax: Float,
        state: SensorState,
        lastReadings: Map<String, SensorReading>,
        selectedSensorIds: Set<String>,
        sensorHistory: Map<String, SensorValueHistory>
    ): SensorGaugeConfig {
        val reading = lastReadings[id]
        val (actualMin, actualMax) = getDynamicRange(id, defaultMin, defaultMax, sensorHistory)

        return SensorGaugeConfig(
            id = id,
            title = title,
            icon = icon,
            reading = reading,
            minValue = actualMin,
            maxValue = actualMax,
            isSelected = selectedSensorIds.contains(id)
        )
    }

    private fun getDynamicRange(
        sensorId: String,
        defaultMin: Float,
        defaultMax: Float,
        sensorHistory: Map<String, SensorValueHistory>
    ): Pair<Float, Float> {
        // Check if this sensor should use dynamic ranges
        if (sensorId in nonDynamicSensors) {
            return defaultMin to defaultMax
        }

        // Get sensor history and calculate dynamic range
        val history = sensorHistory[sensorId]
        return if (history != null && history.values.isNotEmpty()) {
            history.getDynamicRange(defaultMin, defaultMax)
        } else {
            defaultMin to defaultMax
        }
    }
} 