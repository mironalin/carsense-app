package com.carsense.features.sensors.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carsense.features.sensors.domain.model.SensorReading
import kotlin.math.cos
import kotlin.math.sin

/**
 * Realistic automotive speedometer gauge with dynamic range
 */
@Composable
fun SpeedGauge(
    sensorReading: SensorReading?,
    maxSpeed: Float = 200f,
    speedLimit: Float = 120f, // Parameter kept for API compatibility but not used
    modifier: Modifier = Modifier
) {
    val currentSpeed = sensorReading?.value?.toFloatOrNull() ?: 0f
    val isError = sensorReading?.isError ?: false
    val isLoading = sensorReading == null

    // Dynamic speed range with smooth transitions - much higher ranges for speed
    var dynamicMaxSpeed by remember { mutableFloatStateOf(maxSpeed) }

    LaunchedEffect(currentSpeed) {
        // Only increase range if we're consistently hitting high speeds
        if (currentSpeed > dynamicMaxSpeed * 0.90f) { // More aggressive scaling at 90%
            val newMaxSpeed = when {
                currentSpeed > 280f -> 320f  // Support for high-performance vehicles
                currentSpeed > 240f -> 280f
                currentSpeed > 200f -> 240f
                currentSpeed > 160f -> 200f
                currentSpeed > 120f -> 160f
                currentSpeed > 80f -> 120f
                else -> dynamicMaxSpeed
            }
            // Only update if it's a significant change
            if (newMaxSpeed > dynamicMaxSpeed) {
                dynamicMaxSpeed = newMaxSpeed
            }
        }
    }

    // Smooth animation for the max speed range changes
    val animatedMaxSpeed by animateFloatAsState(
        targetValue = dynamicMaxSpeed,
        animationSpec = tween(durationMillis = 1500), // Smooth 1.5s transition
        label = "maxSpeedAnimation"
    )

    // Smooth needle animation with custom interpolation for better feel
    val animatedSpeed by animateFloatAsState(
        targetValue = if (isError || isLoading) 0f else currentSpeed,
        animationSpec = tween(
            durationMillis = 150, // Responsive timing
            easing = FastOutSlowInEasing // Smooth but responsive easing
        ),
        label = "speedValue"
    )

    // No speed limit warnings - this is a speedometer, not a tachometer

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f) // Ensure perfect circle
            .shadow(20.dp, CircleShape) // Deeper shadow for premium look
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF000000),     // Pure black center
                        Color(0xFF0A0A0A),     // Very dark gray
                        Color(0xFF1A1A1A),     // Dark gray middle
                        Color(0xFF0F0F0F),     // Almost black
                        Color(0xFF000000)      // Black outer edge
                    ),
                    radius = 400f
                )
            )
    ) {
        // Calculate scale factor based on actual size vs baseline (280dp)
        val scaleFactor = with(LocalDensity.current) {
            minOf(maxWidth.toPx(), maxHeight.toPx()) / 280.dp.toPx()
        }

        // Ambient glow effect around the entire gauge - blue tint for speedometer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val canvasScaleFactor = size.minDimension / 280.dp.toPx()

            // Subtle blue ambient lighting for speed differentiation
            for (i in 1..3) {
                val glowRadius = size.minDimension / 2 - (i * 12 * canvasScaleFactor)
                drawCircle(
                    color = Color(0xFF2196F3).copy(alpha = 0.02f + (i * 0.008f)),
                    radius = glowRadius,
                    center = center,
                    style = Stroke(width = (i * 2 * canvasScaleFactor))
                )
            }
        }

        // Main gauge canvas
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasScaleFactor = size.minDimension / 280.dp.toPx()
            drawProfessionalSpeedometer(
                speed = animatedSpeed,
                maxSpeed = animatedMaxSpeed,
                isError = isError,
                scaleFactor = canvasScaleFactor
            )
        }

        // Subtle digital display positioned to avoid overlap
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = (140 * scaleFactor).dp)
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                Text(
                    text = "---",
                    fontSize = (18 * scaleFactor).sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF888888).copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            } else if (isError) {
                Text(
                    text = "ERR",
                    fontSize = (16 * scaleFactor).sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFCC4444).copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "${currentSpeed.toInt()}",
                    fontSize = (20 * scaleFactor).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64B5F6).copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "KM/H",
                fontSize = (9 * scaleFactor).sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF666666).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Max range indicator in bottom corner - very subtle
        Text(
            text = "MAX ${animatedMaxSpeed.toInt()}",
            fontSize = (7 * scaleFactor).sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF666666).copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (20 * scaleFactor).dp)
        )
    }
}

/**
 * Draws professional speedometer with automotive styling
 */
private fun DrawScope.drawProfessionalSpeedometer(
    speed: Float,
    maxSpeed: Float,
    isError: Boolean,
    scaleFactor: Float
) {
    val center = size.center
    val outerRadius = size.minDimension / 2 - 15.dp.toPx()

    // Draw speedometer-specific bezel rings with blue accents
    drawSpeedometerBezelRings(center, outerRadius, scaleFactor)

    // Draw speedometer tick marks with different styling
    drawSpeedometerTickMarks(center, outerRadius, maxSpeed, scaleFactor)

    // Draw clean speedometer dial face (no warning zones)
    drawCleanSpeedometerDial(center, outerRadius, maxSpeed, scaleFactor)

    // Draw the needle with blue styling
    if (!isError) {
        drawSpeedometerNeedle(center, outerRadius, speed, maxSpeed, scaleFactor)
    }

    // Draw speedometer center hub
    drawSpeedometerCenterHub(center, scaleFactor)
}

/**
 * Speedometer bezel rings with blue accents for differentiation
 */
private fun DrawScope.drawSpeedometerBezelRings(
    center: Offset,
    radius: Float,
    scaleFactor: Float
) {
    // Outer ring with blue-tinted metallic gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF6688AA), // Blue-tinted metal
                Color(0xFF334455),
                Color(0xFF445566),
                Color(0xFF223344)
            )
        ),
        radius = radius + 25.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 4.dp.toPx() * scaleFactor)
    )

    // Inner ring with blue accent
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF334455),
                Color(0xFF1A2A3A), // Blue-tinted dark
                Color(0xFF223344)
            )
        ),
        radius = radius + 15.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 3.dp.toPx() * scaleFactor)
    )

    // Subtle blue accent ring
    drawCircle(
        color = Color(0xFF1976D2).copy(alpha = 0.3f),
        radius = radius + 20.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 1.dp.toPx() * scaleFactor)
    )
}

/**
 * Clean speedometer dial face without warning zones
 */
private fun DrawScope.drawCleanSpeedometerDial(
    center: Offset,
    radius: Float,
    maxSpeed: Float,
    scaleFactor: Float
) {
    val dialRadius = radius - 25.dp.toPx() * scaleFactor // Same position as before but cleaner

    // Create clean dial face background with blue tinting
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF0A0A0F),     // Very dark blue-tinted
                Color(0xFF050505),     // Almost black
                Color(0xFF050A0F)      // Very dark blue-tinted
            ),
            radius = dialRadius + 8.dp.toPx() * scaleFactor
        ),
        radius = dialRadius + 8.dp.toPx() * scaleFactor,
        center = center
    )

    // Draw subtle groove around the dial
    drawCircle(
        color = Color(0xFF000000).copy(alpha = 0.8f),
        radius = dialRadius + 8.dp.toPx() * scaleFactor + 1.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 1.dp.toPx() * scaleFactor)
    )

    // Inner highlight ring with blue tint
    drawCircle(
        color = Color(0xFF1976D2).copy(alpha = 0.2f),
        radius = dialRadius - 1.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 1.dp.toPx() * scaleFactor)
    )

    // Subtle inner shadow for depth
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF000000).copy(alpha = 0.4f)
            ),
            radius = dialRadius - 5.dp.toPx() * scaleFactor
        ),
        radius = dialRadius - 5.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 3.dp.toPx() * scaleFactor)
    )

    // Add subtle speed markings on the dial face
    drawSubtleSpeedMarkings(center, radius - 45.dp.toPx() * scaleFactor, maxSpeed, scaleFactor)
}

/**
 * Speedometer tick marks with automotive spacing
 */
private fun DrawScope.drawSpeedometerTickMarks(
    center: Offset,
    radius: Float,
    maxSpeed: Float,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f
    val majorTickLength = 20.dp.toPx() * scaleFactor
    val minorTickLength = 12.dp.toPx() * scaleFactor
    val microTickLength = 6.dp.toPx() * scaleFactor

    // Standard automotive speedometer - always 20 km/h increments like real cars
    val majorIncrement = 20f // Always 20 km/h increments: 0, 20, 40, 60, 80, 100, 120, etc.

    val minorIncrement = 10f  // Minor ticks every 10 km/h: 10, 30, 50, 70, etc.
    val microIncrement = 5f   // Micro ticks every 5 km/h for fine detail

    // Draw micro ticks - very subtle
    val microTicks = (maxSpeed / microIncrement).toInt()
    for (i in 0..microTicks) {
        val speed = i * microIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * speed / maxSpeed)).toDouble())

        if (speed % majorIncrement != 0f && speed % minorIncrement != 0f) {
            val tickStart = Offset(
                center.x + ((radius - microTickLength) * cos(angle)).toFloat(),
                center.y + ((radius - microTickLength) * sin(angle)).toFloat()
            )
            val tickEnd = Offset(
                center.x + (radius * cos(angle)).toFloat(),
                center.y + (radius * sin(angle)).toFloat()
            )

            drawLine(
                color = Color(0xFF666666).copy(alpha = 0.4f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 0.5.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )
        }
    }

    // Draw minor ticks - subtle
    val minorTicks = (maxSpeed / minorIncrement).toInt()
    for (i in 0..minorTicks) {
        val speed = i * minorIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * speed / maxSpeed)).toDouble())

        if (speed % majorIncrement != 0f) {
            val tickStart = Offset(
                center.x + ((radius - minorTickLength) * cos(angle)).toFloat(),
                center.y + ((radius - minorTickLength) * sin(angle)).toFloat()
            )
            val tickEnd = Offset(
                center.x + (radius * cos(angle)).toFloat(),
                center.y + (radius * sin(angle)).toFloat()
            )

            drawLine(
                color = Color(0xFFBBBBBB).copy(alpha = 0.7f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 1.5.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )
        }
    }

    // Draw major ticks with numbers - professional styling
    val majorTicks = (maxSpeed / majorIncrement).toInt()
    for (i in 0..majorTicks) {
        val speed = i * majorIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * speed / maxSpeed)).toDouble())

        // Major tick mark
        val tickStart = Offset(
            center.x + ((radius - majorTickLength) * cos(angle)).toFloat(),
            center.y + ((radius - majorTickLength) * sin(angle)).toFloat()
        )
        val tickEnd = Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )

        // Subtle glow for major ticks
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = tickStart,
            end = tickEnd,
            strokeWidth = 5.dp.toPx() * scaleFactor,
            cap = StrokeCap.Round
        )

        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = tickStart,
            end = tickEnd,
            strokeWidth = 2.5.dp.toPx() * scaleFactor,
            cap = StrokeCap.Round
        )

        // Speed numbers - subtle and professional
        val numberRadius = radius - 38.dp.toPx() * scaleFactor
        val numberX = center.x + (numberRadius * cos(angle)).toFloat()
        val numberY = center.y + (numberRadius * sin(angle)).toFloat()

        val displayNumber = "${speed.toInt()}"

        drawContext.canvas.nativeCanvas.drawText(
            displayNumber,
            numberX,
            numberY + 4.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    160,
                    187,
                    221,
                    255
                ) // Light blue tint for speedometer
                textSize = 14.sp.toPx() * scaleFactor // Smaller than before
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT
                setShadowLayer(
                    3.dp.toPx() * scaleFactor,
                    0f,
                    0f,
                    android.graphics.Color.argb(80, 255, 255, 255)
                )
            }
        )
    }
}

/**
 * Speedometer needle with blue styling for differentiation
 */
private fun DrawScope.drawSpeedometerNeedle(
    center: Offset,
    radius: Float,
    speed: Float,
    maxSpeed: Float,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f
    val percentage = (speed / maxSpeed).coerceIn(0f, 1f)
    val needleAngle = Math.toRadians((startAngle + (totalSweep * percentage)).toDouble())
    val needleLength = radius - 35.dp.toPx() * scaleFactor

    val needleTip = Offset(
        center.x + (needleLength * cos(needleAngle)).toFloat(),
        center.y + (needleLength * sin(needleAngle)).toFloat()
    )

    // Blue glow layers for speedometer needle
    for (i in 1..4) {
        val glowWidth = (8 - i).dp.toPx() * scaleFactor
        val glowAlpha = 0.12f - (i * 0.02f) // Slightly more intense blue glow

        drawLine(
            color = Color(0xFF1976D2).copy(alpha = glowAlpha), // Deeper blue glow
            start = center,
            end = needleTip,
            strokeWidth = glowWidth,
            cap = StrokeCap.Round
        )
    }

    // Main needle body - blue-tinted metallic for speedometer
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFE3F2FD), // Light blue-white
                Color(0xFFBBDEFB), // Light blue
                Color(0xFF90CAF9)  // Medium blue
            )
        ),
        start = center,
        end = needleTip,
        strokeWidth = 2.5.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )

    // Subtle needle tip accent
    val tipLength = 25.dp.toPx() * scaleFactor
    val needleTipStart = Offset(
        center.x + ((needleLength - tipLength) * cos(needleAngle)).toFloat(),
        center.y + ((needleLength - tipLength) * sin(needleAngle)).toFloat()
    )

    // Tip with very subtle glow - blue for speed instead of red
    drawLine(
        color = Color(0xFF2196F3).copy(alpha = 0.1f),
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 5.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )

    drawLine(
        color = Color(0xFF2196F3).copy(alpha = 0.9f),
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 2.5.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )
}

/**
 * Speedometer center hub with blue accents
 */
private fun DrawScope.drawSpeedometerCenterHub(center: Offset, scaleFactor: Float) {
    // Blue-tinted outer glow for speedometer
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1976D2).copy(alpha = 0.6f), // Blue tint
                Color(0xFF0D47A1).copy(alpha = 0.3f), // Darker blue
                Color.Transparent
            )
        ),
        radius = 20.dp.toPx() * scaleFactor,
        center = center
    )

    // Main hub
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF666666),
                Color(0xFF333333),
                Color(0xFF222222)
            )
        ),
        radius = 12.dp.toPx() * scaleFactor,
        center = center
    )

    // Inner ring with blue tint
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFBBDEFB), // Light blue
                Color(0xFF90CAF9), // Blue
                Color(0xFF64B5F6)  // Slightly darker blue
            )
        ),
        radius = 8.dp.toPx() * scaleFactor,
        center = center
    )

    // Center point with blue tint
    drawCircle(
        color = Color(0xFF6688BB),
        radius = 4.dp.toPx() * scaleFactor,
        center = center
    )

    // Subtle blue highlight
    drawCircle(
        color = Color(0xFFAADDFF),
        radius = 1.5.dp.toPx() * scaleFactor,
        center = center.copy(
            x = center.x - 1.dp.toPx() * scaleFactor,
            y = center.y - 1.dp.toPx() * scaleFactor
        )
    )
}

/**
 * Draw subtle speed digit markings on the dial face
 */
private fun DrawScope.drawSubtleSpeedMarkings(
    center: Offset,
    radius: Float,
    maxSpeed: Float,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f

    // Calculate optimal spacing for speed markings
    val majorIncrement = 20f // Always 20 km/h increments: 0, 20, 40, 60, 80, 100, 120, etc.

    val majorTicks = (maxSpeed / majorIncrement).toInt()
    for (i in 0..majorTicks) {
        val speed = i * majorIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * speed / maxSpeed)).toDouble())

        // Speed numbers on dial face - very subtle
        val numberX = center.x + (radius * cos(angle)).toFloat()
        val numberY = center.y + (radius * sin(angle)).toFloat()

        val displayNumber = "${speed.toInt()}"

        drawContext.canvas.nativeCanvas.drawText(
            displayNumber,
            numberX,
            numberY + 4.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    160,
                    187,
                    221,
                    255
                ) // Light blue tint for speedometer
                textSize = 14.sp.toPx() * scaleFactor // Smaller than before
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT
                setShadowLayer(
                    2f * scaleFactor,
                    0f,
                    0f,
                    android.graphics.Color.argb(60, 255, 255, 255)
                )
            }
        )
    }
} 