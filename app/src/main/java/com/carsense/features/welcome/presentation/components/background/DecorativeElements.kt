package com.carsense.features.welcome.presentation.components.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Component that creates decorative elements like dots, lines, and patterns
 * for the welcome screen background.
 */
@Composable
fun DecorativeElements(
    slowRotation: Float,
    fastRotation: Float,
    floatX1: Float,
    floatX2: Float,
    floatX3: Float,
    floatY1: Float,
    floatY2: Float,
    floatY3: Float,
    floatY4: Float,
    modifier: Modifier = Modifier
) {
    // Additional decorative elements to fill space
    // Floating dots in corners
    repeat(6) { index ->
        val floatXVariant = when (index % 3) {
            0 -> floatX1
            1 -> floatX2
            else -> floatX3
        }
        val floatYVariant = when (index % 4) {
            0 -> floatY1
            1 -> floatY2
            2 -> floatY3
            else -> floatY4
        }
        Box(
            modifier = Modifier
                .size((8 + index * 3).dp)
                .offset(
                    x = (50 + index * 45).dp + (floatXVariant * (0.3f + index * 0.1f)).dp,
                    y = (80 + index * 90).dp + (floatYVariant * (0.2f + index * 0.05f)).dp
                )
                .alpha(0.04f + index * 0.01f)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    shape = CircleShape
                )
        )
    }

    // Subtle gradient bars for visual interest
    repeat(4) { index ->
        Box(
            modifier = Modifier
                .size(width = (40 + index * 15).dp, height = 2.dp)
                .offset(
                    x = (30 + index * 70).dp,
                    y = (200 + index * 100).dp + floatY2.dp * 0.2f
                )
                .rotate(45f + index * 30f)
                .alpha(0.05f)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                        )
                    )
                )
        )
    }

    // Automotive-inspired circuit patterns
    repeat(8) { index ->
        val floatXVariant = when (index % 3) {
            0 -> floatX1
            1 -> floatX2
            else -> floatX3
        }
        val floatYVariant = when (index % 4) {
            0 -> floatY1
            1 -> floatY2
            2 -> floatY3
            else -> floatY4
        }
        Box(
            modifier = Modifier
                .size(width = (20 + index * 8).dp, height = 1.dp)
                .offset(
                    x = (40 + index * 35).dp + (floatXVariant * (0.2f + index * 0.05f)).dp,
                    y = (120 + index * 60).dp + (floatYVariant * (0.1f + index * 0.02f)).dp
                )
                .rotate(if (index % 2 == 0) 0f else 90f)
                .alpha(0.03f + index * 0.005f)
                .background(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                )
        )
    }

    // Tech-inspired hexagonal patterns
    repeat(5) { index ->
        Box(
            modifier = Modifier
                .size((12 + index * 4).dp)
                .offset(
                    x = (80 + index * 60).dp + (floatX2 * (0.3f + index * 0.1f)).dp,
                    y = (180 + index * 80).dp + (floatY3 * (0.2f + index * 0.05f)).dp
                )
                .rotate(slowRotation * 0.3f + index * 60f)
                .alpha(0.02f + index * 0.008f)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.01f)
                        )
                    ),
                    shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                        // Create hexagon shape
                        val radius = size.minDimension / 2
                        val centerX = size.width / 2
                        val centerY = size.height / 2

                        moveTo(centerX + radius, centerY)
                        for (i in 1..6) {
                            val angle = i * 60.0 * Math.PI / 180.0
                            lineTo(
                                (centerX + radius * kotlin.math.cos(angle)).toFloat(),
                                (centerY + radius * kotlin.math.sin(angle)).toFloat()
                            )
                        }
                        close()
                    }
                )
        )
    }

    // Floating diagnostic symbols (subtle OBD2 inspired elements)
    repeat(6) { index ->
        Box(
            modifier = Modifier
                .size((6 + index * 2).dp)
                .offset(
                    x = (60 + index * 50).dp + (floatX1 * (0.2f + index * 0.1f)).dp,
                    y = (100 + index * 70).dp + (floatY2 * (0.3f + index * 0.05f)).dp
                )
                .rotate(fastRotation * (0.5f + index * 0.2f))
                .alpha(0.04f + index * 0.01f)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                        )
                    ),
                    shape = if (index % 3 == 0) CircleShape else RoundedCornerShape(2.dp)
                )
        )
    }

    // Data stream lines (representing vehicle data flow)
    repeat(6) { index ->
        Box(
            modifier = Modifier
                .size(width = (60 + index * 20).dp, height = 1.dp)
                .offset(
                    x = (20 + index * 40).dp + (floatX3 * (0.1f + index * 0.03f)).dp,
                    y = (250 + index * 50).dp + (floatY4 * (0.15f + index * 0.02f)).dp
                )
                .rotate(15f + index * 10f)
                .alpha(0.03f + index * 0.005f)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.0f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.0f)
                        )
                    )
                )
        )
    }

    // Corner accent elements for frame-like effect
    repeat(4) { corner ->
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 3.dp)
                .offset(
                    x = if (corner % 2 == 0) 10.dp + (floatX1 * 0.2f).dp else 320.dp + (floatX2 * 0.2f).dp,
                    y = if (corner < 2) 20.dp + (floatY1 * 0.3f).dp else 600.dp + (floatY3 * 0.3f).dp
                )
                .rotate(if (corner % 2 == 0) 0f else 180f)
                .alpha(0.06f)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
} 