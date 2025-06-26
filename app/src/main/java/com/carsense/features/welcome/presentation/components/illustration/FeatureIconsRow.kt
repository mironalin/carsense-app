package com.carsense.features.welcome.presentation.components.illustration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Zap

/**
 * Row of animated feature icons for the welcome center illustration.
 */
@Composable
fun FeatureIconsRow(
    subtleRotation: Float,
    floatY: Float,
    modifier: Modifier = Modifier
) {
    // Feature icons row with subtle animations (no additional background)
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(16.dp)
            .alpha(0.95f)
    ) {
        // Diagnostics feature - subtle left-right rotation
        FeatureIcon(
            icon = Lucide.Activity,
            rotation = subtleRotation,
            floatOffset = floatY
        )

        // Performance feature - opposite rotation direction
        FeatureIcon(
            icon = Lucide.Gauge,
            rotation = -subtleRotation * 0.8f,
            floatOffset = -floatY
        )

        // Analytics feature - different timing
        FeatureIcon(
            icon = Lucide.Zap,
            rotation = subtleRotation * 0.6f,
            floatOffset = floatY * 0.7f
        )
    }
} 