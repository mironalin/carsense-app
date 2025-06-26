package com.carsense.features.welcome.presentation.components.illustration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Animated feature icon component with rotation and floating animations.
 */
@Composable
fun FeatureIcon(
    icon: ImageVector,
    rotation: Float,
    floatOffset: Float,
    modifier: Modifier = Modifier
) {
    // Container box that's larger to accommodate rotation animations
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(72.dp)  // Increased to 72dp to accommodate 15° rotation
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .rotate(rotation)
                .offset(y = floatOffset.dp),  // Move offset to the surface itself
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 