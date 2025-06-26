package com.carsense.features.welcome.presentation.components.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Component that creates floating circles with various sizes and animations
 * for the welcome screen background.
 */
@Composable
fun FloatingCircles(
    slowRotation: Float,
    mediumRotation: Float,
    fastRotation: Float,
    floatX1: Float,
    floatX2: Float,
    floatX3: Float,
    floatY1: Float,
    floatY2: Float,
    floatY3: Float,
    floatY4: Float,
    pulseAlpha: Float,
    modifier: Modifier = Modifier
) {
    // Large background circle - top left
    Box(
        modifier = Modifier
            .size(200.dp)
            .offset(x = (-50).dp + floatX1.dp, y = (-50).dp + floatY1.dp)
            .rotate(slowRotation)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                    )
                ),
                shape = CircleShape
            )
    )

    // Medium circle - top right
    Box(
        modifier = Modifier
            .size(120.dp)
            .offset(x = 280.dp + floatX2.dp, y = (-20).dp + floatY2.dp)
            .rotate(mediumRotation)
            .alpha(pulseAlpha)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.03f)
                    )
                ),
                shape = CircleShape
            )
    )

    // Small circle - middle left
    Box(
        modifier = Modifier
            .size(80.dp)
            .offset(x = (-30).dp + floatX3.dp, y = 300.dp + floatY3.dp)
            .rotate(fastRotation)
            .background(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                shape = CircleShape
            )
    )

    // Medium circle - bottom right
    Box(
        modifier = Modifier
            .size(140.dp)
            .offset(x = 250.dp + floatX1.dp, y = 400.dp + floatY4.dp)
            .rotate(-slowRotation)
            .alpha(pulseAlpha * 0.8f)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.02f)
                    )
                ),
                shape = CircleShape
            )
    )

    // Small accent circle - bottom left
    Box(
        modifier = Modifier
            .size(60.dp)
            .offset(x = 20.dp + floatX2.dp, y = 500.dp + floatY1.dp)
            .rotate(mediumRotation)
            .background(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f),
                shape = CircleShape
            )
    )

    // Extra small floating elements for depth
    repeat(3) { index ->
        val floatXVariant = when (index) {
            0 -> floatX1
            1 -> floatX2
            else -> floatX3
        }
        val floatYVariant = when (index) {
            0 -> floatY1
            1 -> floatY3
            else -> floatY4
        }
        Box(
            modifier = Modifier
                .size((25 + index * 10).dp)
                .offset(
                    x = (100 + index * 80).dp + (floatXVariant * (0.8f + index * 0.3f)).dp,
                    y = (150 + index * 120).dp + (floatYVariant * (0.6f + index * 0.2f)).dp
                )
                .rotate(fastRotation * (0.7f + index * 0.4f))
                .alpha(0.03f + index * 0.02f)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = CircleShape
                )
        )
    }
} 