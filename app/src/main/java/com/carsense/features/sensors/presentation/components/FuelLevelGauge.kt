package com.carsense.features.sensors.presentation.components

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
import androidx.compose.runtime.getValue
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
 * Professional automotive fuel level gauge with percentage display
 * Visual styling different from coolant gauge with blue/amber color scheme
 */
@Composable
fun FuelLevelGauge(
    modifier: Modifier = Modifier,
    sensorReading: SensorReading?,
    minLevel: Float = 0f,   // 0%
    maxLevel: Float = 100f  // 100%
) {
    val currentLevel = sensorReading?.value?.toFloatOrNull() ?: minLevel
    val isError = sensorReading?.isError ?: false
    val isLoading = sensorReading == null

    // Check if fuel level is low (below 20%)
    val isLowFuel = currentLevel <= 20f && !isError && !isLoading

    // Smooth fuel level animation
    val animatedLevel by animateFloatAsState(
        targetValue = if (isError || isLoading) minLevel else currentLevel,
        animationSpec = tween(durationMillis = 200),
        label = "fuelLevel"
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .shadow(18.dp, CircleShape)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF001122),     // Dark blue center
                        Color(0xFF002244),     // Darker blue
                        Color(0xFF003366),     // Medium blue
                        Color(0xFF001133),     // Dark blue middle
                        Color(0xFF000000)      // Black outer edge
                    ),
                    radius = 350f
                )
            )
    ) {
        // Calculate scale factor based on actual size vs baseline (280dp)
        val scaleFactor = with(LocalDensity.current) {
            minOf(maxWidth.toPx(), maxHeight.toPx()) / 280.dp.toPx()
        }
        // Ambient glow effect with blue tint
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val canvasScaleFactor = size.minDimension / 280.dp.toPx()

            for (i in 1..3) {
                val glowRadius = size.minDimension / 2 - (i * 12 * canvasScaleFactor)
                drawCircle(
                    color = Color(0xFF4488DD).copy(alpha = 0.02f + (i * 0.008f)),
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
            drawProfessionalFuelGauge(
                fuelLevel = animatedLevel,
                minLevel = minLevel,
                maxLevel = maxLevel,
                isError = isError,
                isLowFuel = isLowFuel,
                scaleFactor = canvasScaleFactor
            )
        }

        // Digital display with blue theme
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
                    color = Color(0xFF6699CC).copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            } else if (isError) {
                Text(
                    text = "ERR",
                    fontSize = (16 * scaleFactor).sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFCC6666).copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "${currentLevel.toInt()}",
                    fontSize = (20 * scaleFactor).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = if (isLowFuel) {
                        Color(0xFFFFAA00).copy(alpha = 0.95f) // Amber for low fuel
                    } else {
                        Color(0xFF88BBDD).copy(alpha = 0.9f) // Light blue for normal
                    },
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "%",
                fontSize = (9 * scaleFactor).sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4488AA).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Fuel level indicator in bottom corner
        Text(
            text = "FUEL ${minLevel.toInt()}-${maxLevel.toInt()}%",
            fontSize = (7 * scaleFactor).sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF336699).copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (20 * scaleFactor).dp)
        )
    }
}

/**
 * Draws professional fuel level gauge with blue/amber color scheme
 * Different visual styling from coolant gauge
 */
private fun DrawScope.drawProfessionalFuelGauge(
    fuelLevel: Float,
    minLevel: Float,
    maxLevel: Float,
    isError: Boolean,
    isLowFuel: Boolean,
    scaleFactor: Float
) {
    val center = size.center
    val outerRadius = size.minDimension / 2 - 15.dp.toPx()

    // Draw professional bezel rings with blue tint
    drawFuelBezelRings(center, outerRadius, scaleFactor)

    // Draw low fuel warning zone (amber zone for levels below 20%)
    drawLowFuelWarningZone(center, outerRadius, minLevel, maxLevel, scaleFactor)

    // Draw fuel level tick marks
    drawFuelLevelTickMarks(center, outerRadius, minLevel, maxLevel, scaleFactor)

    // Draw the needle
    if (!isError) {
        drawProfessionalFuelNeedle(
            center,
            outerRadius,
            fuelLevel,
            minLevel,
            maxLevel,
            isLowFuel,
            scaleFactor
        )
    }

    // Draw center hub
    drawFuelCenterHub(center, scaleFactor)
}

/**
 * Professional bezel rings with blue metallic styling
 */
private fun DrawScope.drawFuelBezelRings(center: Offset, radius: Float, scaleFactor: Float) {
    // Outer ring with blue metallic gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF6699BB), // Light blue metallic
                Color(0xFF445577),
                Color(0xFF556688),
                Color(0xFF223344)
            )
        ),
        radius = radius + 20.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 3.dp.toPx() * scaleFactor)
    )

    // Inner ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF445577),
                Color(0xFF1A2A3A),
                Color(0xFF334455)
            )
        ),
        radius = radius + 12.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 2.dp.toPx() * scaleFactor)
    )
}

/**
 * Low fuel warning zone (amber zone for levels below 20%)
 */
private fun DrawScope.drawLowFuelWarningZone(
    center: Offset,
    radius: Float,
    minLevel: Float,
    maxLevel: Float,
    scaleFactor: Float
) {
    // Warning zone: 0% to 20%
    val warningEndLevel = 20f
    val startAngle = 135f    // Bottom-left
    val totalSweep = 270f

    // Calculate angle for 20% (end of warning zone)
    val warningEndAngle =
        startAngle + ((warningEndLevel - minLevel) / (maxLevel - minLevel)) * totalSweep

    val sweepAngle = warningEndAngle - startAngle

    // Draw amber warning zone
    val warningRect = androidx.compose.ui.geometry.Rect(
        center = center,
        radius = radius - 8.dp.toPx() * scaleFactor
    )

    // Outer amber glow
    drawArc(
        color = Color(0xFFFF8800).copy(alpha = 0.3f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 16.dp.toPx() * scaleFactor),
        topLeft = warningRect.topLeft,
        size = warningRect.size
    )

    // Inner amber accent
    drawArc(
        color = Color(0xFFFFBB44).copy(alpha = 0.6f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 3.dp.toPx() * scaleFactor),
        topLeft = warningRect.topLeft,
        size = warningRect.size
    )

    // Add "LOW" text marker
    val midWarningAngle = startAngle + (sweepAngle / 2f)
    val textRadius = radius - 35.dp.toPx() * scaleFactor
    val textAngle = Math.toRadians(midWarningAngle.toDouble())
    val textX = center.x + (textRadius * cos(textAngle)).toFloat()
    val textY = center.y + (textRadius * sin(textAngle)).toFloat()

    drawContext.canvas.nativeCanvas.drawText(
        "LOW",
        textX,
        textY + 3.dp.toPx() * scaleFactor,
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, 255, 187, 68)
            textSize = 9.sp.toPx() * scaleFactor
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(
                3.dp.toPx() * scaleFactor,
                0f,
                0f,
                android.graphics.Color.argb(150, 0, 0, 0)
            )
        }
    )
}

/**
 * Fuel level tick marks with blue color scheme
 */
private fun DrawScope.drawFuelLevelTickMarks(
    center: Offset,
    radius: Float,
    minLevel: Float,
    maxLevel: Float,
    scaleFactor: Float
) {
    val startAngle = 135f    // Bottom-left
    val totalSweep = 270f

    val majorTickLength = 16.dp.toPx() * scaleFactor
    val minorTickLength = 10.dp.toPx() * scaleFactor

    // More tick marks for percentage display
    val totalTicks = 20 // Creates 21 sections (0, 5, 10, 15, ... 100%)

    for (i in 0..totalTicks) {
        val progress = i.toFloat() / totalTicks.toFloat()
        val currentAngle = startAngle + (progress * totalSweep)
        val drawingAngle = if (currentAngle > 360f) currentAngle - 360f else currentAngle
        val angle = Math.toRadians(drawingAngle.toDouble())

        // Major ticks every 25% (0, 25, 50, 75, 100)
        val isMajorTick = i % 5 == 0
        val tickLength = if (isMajorTick) majorTickLength else minorTickLength

        val tickStart = Offset(
            center.x + ((radius - tickLength) * cos(angle)).toFloat(),
            center.y + ((radius - tickLength) * sin(angle)).toFloat()
        )
        val tickEnd = Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )

        if (isMajorTick) {
            val percentage = (progress * (maxLevel - minLevel) + minLevel).toInt()
            val isEmpty = percentage == 0
            val isFull = percentage == 100
            val isLowLevel = percentage <= 20

            val tickColor = when {
                isLowLevel -> Color(0xFFFFAA00) // Amber for low levels
                isFull -> Color(0xFF44AA88)     // Green for full
                else -> Color(0xFF88BBDD)       // Light blue for normal
            }

            // Enhanced glow for major ticks
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        tickColor.copy(alpha = 0.1f),
                        tickColor.copy(alpha = 0.4f),
                        tickColor.copy(alpha = 0.1f)
                    )
                ),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 6.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )

            // Main tick line
            drawLine(
                color = tickColor,
                start = tickStart,
                end = tickEnd,
                strokeWidth = 3.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )

            // Percentage numbers for major ticks
            val numberRadius = radius - 30.dp.toPx() * scaleFactor
            val numberX = center.x + (numberRadius * cos(angle)).toFloat()
            val numberY = center.y + (numberRadius * sin(angle)).toFloat()

            drawContext.canvas.nativeCanvas.drawText(
                percentage.toString(),
                numberX,
                numberY + 4.dp.toPx() * scaleFactor,
                android.graphics.Paint().apply {
                    color = when {
                        isLowLevel -> android.graphics.Color.argb(255, 255, 170, 0)
                        isFull -> android.graphics.Color.argb(255, 68, 170, 136)
                        else -> android.graphics.Color.argb(255, 136, 187, 221)
                    }
                    textSize =
                        if (isEmpty || isFull) 16.sp.toPx() * scaleFactor else 14.sp.toPx() * scaleFactor
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setShadowLayer(
                        3.dp.toPx() * scaleFactor,
                        0f,
                        0f,
                        android.graphics.Color.argb(120, 0, 0, 0)
                    )
                }
            )
        } else {
            // Minor tick with blue tint
            drawLine(
                color = Color(0xFF6699BB).copy(alpha = 0.5f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 1.5.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Professional fuel needle with blue/amber styling
 */
private fun DrawScope.drawProfessionalFuelNeedle(
    center: Offset,
    radius: Float,
    fuelLevel: Float,
    minLevel: Float,
    maxLevel: Float,
    isLowFuel: Boolean,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f
    val clampedLevel = fuelLevel.coerceIn(minLevel, maxLevel)

    val progress = (clampedLevel - minLevel) / (maxLevel - minLevel)
    val needleAngleDegrees = startAngle + (progress * totalSweep)
    val drawingAngle =
        if (needleAngleDegrees > 360f) needleAngleDegrees - 360f else needleAngleDegrees
    val needleAngle = Math.toRadians(drawingAngle.toDouble())
    val needleLength = radius - 25.dp.toPx() * scaleFactor

    val needleTip = Offset(
        center.x + (needleLength * cos(needleAngle)).toFloat(),
        center.y + (needleLength * sin(needleAngle)).toFloat()
    )

    // Needle glow layers
    for (i in 1..3) {
        val glowWidth = (6 - i).dp.toPx() * scaleFactor
        val glowAlpha = 0.1f - (i * 0.025f)

        drawLine(
            color = if (isLowFuel) {
                Color(0xFFFFAA00).copy(alpha = glowAlpha) // Amber glow for low fuel
            } else {
                Color(0xFF88BBDD).copy(alpha = glowAlpha) // Blue glow for normal
            },
            start = center,
            end = needleTip,
            strokeWidth = glowWidth,
            cap = StrokeCap.Round
        )
    }

    // Main needle body
    drawLine(
        brush = if (isLowFuel) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFCC66), // Light amber
                    Color(0xFFFFAA00), // Amber
                    Color(0xFFCC8800)  // Dark amber tip
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFAADDFF), // Light blue
                    Color(0xFF88BBDD), // Blue
                    Color(0xFF6699BB)  // Dark blue tip
                )
            )
        },
        start = center,
        end = needleTip,
        strokeWidth = 2.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )

    // Needle tip highlight
    val tipLength = 20.dp.toPx() * scaleFactor
    val needleTipStart = Offset(
        center.x + ((needleLength - tipLength) * cos(needleAngle)).toFloat(),
        center.y + ((needleLength - tipLength) * sin(needleAngle)).toFloat()
    )

    drawLine(
        color = if (isLowFuel) {
            Color(0xFFFFDD88).copy(alpha = 0.9f)
        } else {
            Color(0xFFBBDDFF).copy(alpha = 0.9f)
        },
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 2.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )
}

/**
 * Professional center hub with blue metallic styling
 */
private fun DrawScope.drawFuelCenterHub(center: Offset, scaleFactor: Float) {
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF6699BB).copy(alpha = 0.6f),
                Color(0xFF334455).copy(alpha = 0.3f),
                Color.Transparent
            )
        ),
        radius = 16.dp.toPx() * scaleFactor,
        center = center
    )

    // Main hub with blue metallic styling
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF6699BB),
                Color(0xFF445577),
                Color(0xFF223344)
            )
        ),
        radius = 10.dp.toPx() * scaleFactor,
        center = center
    )

    // Inner ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFAABBCC),
                Color(0xFF8899AA),
                Color(0xFF667788)
            )
        ),
        radius = 6.dp.toPx() * scaleFactor,
        center = center
    )

    // Center point
    drawCircle(
        color = Color(0xFF6699BB),
        radius = 3.dp.toPx() * scaleFactor,
        center = center
    )

    // Highlight
    drawCircle(
        color = Color(0xFFCCDDEE),
        radius = 1.dp.toPx() * scaleFactor,
        center = center.copy(
            x = center.x - 0.5.dp.toPx() * scaleFactor,
            y = center.y - 0.5.dp.toPx() * scaleFactor
        )
    )
} 