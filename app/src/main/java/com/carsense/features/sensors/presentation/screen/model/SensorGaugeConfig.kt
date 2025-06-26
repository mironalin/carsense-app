package com.carsense.features.sensors.presentation.screen.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.carsense.features.sensors.domain.model.SensorReading

/**
 * Data class for sensor configuration with gauge-specific parameters
 */
data class SensorGaugeConfig(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val reading: SensorReading?,
    val minValue: Float,
    val maxValue: Float,
    val isSelected: Boolean = false
) 