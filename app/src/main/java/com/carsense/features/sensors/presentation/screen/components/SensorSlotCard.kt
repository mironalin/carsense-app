package com.carsense.features.sensors.presentation.screen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carsense.features.sensors.presentation.screen.model.SensorGaugeConfig
import com.carsense.features.sensors.presentation.viewmodel.SensorState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X

/**
 * Composable that represents a sensor slot - either showing a gauge or a placeholder
 */
@Composable
fun SensorSlotCard(
    slotIndex: Int,
    sensorId: String?,
    sensorConfig: SensorGaugeConfig?,
    state: SensorState,
    isGridLayout: Boolean,
    onAddSensor: () -> Unit,
    onRemoveSensor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (sensorId != null) {
                    // Actual gauge cards: optimized sizes for better screen usage
                    if (isGridLayout) it.height(200.dp) else it.height(300.dp) // Further reduced sizes
                } else {
                    // Placeholder cards: keep them compact
                    if (isGridLayout) it.height(100.dp) else it.height(100.dp) // Reduced from 120dp
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (sensorId != null) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = if (sensorId == null) {
            BorderStroke(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        } else null
    ) {
        if (sensorId != null && sensorConfig != null) {
            // Show actual gauge
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Optimized gauge sizing for better screen usage
                val gaugeSize = if (isGridLayout) {
                    // Grid layout: compact size for 2x3 grid
                    180.dp // Reduced from 220dp to 180dp
                } else {
                    // List layout: larger but not excessive
                    280.dp // Reduced from 320dp to 280dp
                }

                SensorGaugeRenderer(
                    sensorId = sensorId,
                    sensorConfig = sensorConfig,
                    gaugeSize = gaugeSize
                )

                // Remove button in top-right corner
                IconButton(
                    onClick = onRemoveSensor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Remove Sensor",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Show placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onAddSensor() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Lucide.Plus,
                        contentDescription = "Add Sensor",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add Sensor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
} 