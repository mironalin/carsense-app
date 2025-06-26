package com.carsense.features.sensors.presentation.screen.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.carsense.features.sensors.presentation.components.CoolantTemperatureGauge
import com.carsense.features.sensors.presentation.components.FuelLevelGauge
import com.carsense.features.sensors.presentation.components.PercentageGauge
import com.carsense.features.sensors.presentation.components.PressureGauge
import com.carsense.features.sensors.presentation.components.RpmTachometer
import com.carsense.features.sensors.presentation.components.SpeedGauge
import com.carsense.features.sensors.presentation.components.TemperatureGauge
import com.carsense.features.sensors.presentation.screen.model.SensorGaugeConfig

/**
 * Renders the appropriate gauge component based on sensor ID
 */
@Composable
fun SensorGaugeRenderer(
    sensorId: String,
    sensorConfig: SensorGaugeConfig,
    gaugeSize: Dp
) {
    when (sensorId) {
        "rpm" -> {
            RpmTachometer(
                sensorReading = sensorConfig.reading,
                maxRpm = sensorConfig.maxValue,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "speed" -> {
            SpeedGauge(
                sensorReading = sensorConfig.reading,
                maxSpeed = sensorConfig.maxValue,
                speedLimit = 120f,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "coolant" -> {
            CoolantTemperatureGauge(
                sensorReading = sensorConfig.reading,
                minTemp = 50f,
                maxTemp = 130f,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "fuel" -> {
            FuelLevelGauge(
                sensorReading = sensorConfig.reading,
                minLevel = 0f,
                maxLevel = 100f,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "intake" -> {
            val isDynamic = sensorConfig.minValue != 0f || sensorConfig.maxValue != 220f
            TemperatureGauge(
                sensorReading = sensorConfig.reading,
                title = "INTAKE AIR",
                minValue = sensorConfig.minValue,
                maxValue = sensorConfig.maxValue,
                isDynamicRange = isDynamic,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "throttle" -> {
            PercentageGauge(
                sensorReading = sensorConfig.reading,
                title = "THROTTLE",
                minValue = 0f,
                maxValue = 100f,
                isDynamicRange = false,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "load" -> {
            PercentageGauge(
                sensorReading = sensorConfig.reading,
                title = "ENGINE LOAD",
                minValue = 0f,
                maxValue = 100f,
                isDynamicRange = false,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "manifold" -> {
            val isDynamic = sensorConfig.minValue != 0f || sensorConfig.maxValue != 250f
            PressureGauge(
                sensorReading = sensorConfig.reading,
                title = "MANIFOLD",
                minValue = sensorConfig.minValue,
                maxValue = sensorConfig.maxValue,
                unit = "kPa",
                isDynamicRange = isDynamic,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "timing" -> {
            PressureGauge(
                sensorReading = sensorConfig.reading,
                title = "TIMING ADV",
                minValue = -64f,
                maxValue = 64f,
                unit = "°",
                isDynamicRange = false,
                modifier = Modifier.size(gaugeSize)
            )
        }

        "maf" -> {
            val isDynamic = sensorConfig.minValue != 0f || sensorConfig.maxValue != 650f
            PressureGauge(
                sensorReading = sensorConfig.reading,
                title = "MASS AIR FLOW",
                minValue = sensorConfig.minValue,
                maxValue = sensorConfig.maxValue,
                unit = "g/s",
                isDynamicRange = isDynamic,
                modifier = Modifier.size(gaugeSize)
            )
        }
    }
} 