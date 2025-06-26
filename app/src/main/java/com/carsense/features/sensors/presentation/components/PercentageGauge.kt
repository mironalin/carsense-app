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
 * Professional automotive percentage gauge for throttle position and engine load
 * Green/orange color scheme with percentage display
 */
@Composable
fun PercentageGauge(
    modifier: Modifier = Modifier,
    sensorReading: SensorReading?,
    title: String = "PERCENTAGE",
    minValue: Float = 0f,   // 0%
    maxValue: Float = 100f, // 100%
    isDynamicRange: Boolean = false // Indicates if using dynamic range
) {
    val currentValue = sensorReading?.value?.toFloatOrNull() ?: minValue
    val isError = sensorReading?.isError ?: false
    val isLoading = sensorReading == null

    // Smooth range animations
    val animatedMinValue by animateFloatAsState(
        targetValue = minValue,
        animationSpec = tween(durationMillis = 1500),
        label = "minValueAnimation"
    )

    val animatedMaxValue by animateFloatAsState(
        targetValue = maxValue,
        animationSpec = tween(durationMillis = 1500),
        label = "maxValueAnimation"
    )

    // Check if value is at high load (above 80%)
    val isHighLoad = currentValue >= (animatedMaxValue * 0.8f) && !isError && !isLoading

    // Smooth value animation
    val animatedValue by animateFloatAsState(
        targetValue = if (isError || isLoading) animatedMinValue else currentValue,
        animationSpec = tween(durationMillis = 200),
        label = "percentageValue"
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
                        Color(0xFF001100),     // Dark green center
                        Color(0xFF002200),     // Darker green
                        Color(0xFF003300),     // Medium green
                        Color(0xFF001122),     // Dark green middle
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

        // Ambient glow effect with green tint
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val canvasScaleFactor = size.minDimension / 280.dp.toPx()

            for (i in 1..3) {
                val glowRadius = size.minDimension / 2 - (i * 12 * canvasScaleFactor)
                drawCircle(
                    color = Color(0xFF44DD88).copy(alpha = 0.02f + (i * 0.008f)),
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
            drawProfessionalPercentageGauge(
                value = animatedValue,
                minValue = animatedMinValue,
                maxValue = animatedMaxValue,
                isError = isError,
                isHighLoad = isHighLoad,
                scaleFactor = canvasScaleFactor
            )
        }

        // Digital display with green theme - proportional sizing
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
                    color = Color(0xFF66CC99).copy(alpha = 0.8f),
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
                    text = "${animatedValue.toInt()}",
                    fontSize = (20 * scaleFactor).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = if (isHighLoad) {
                        Color(0xFFFF8800).copy(alpha = 0.95f) // Orange for high load
                    } else {
                        Color(0xFF88DDAA).copy(alpha = 0.9f) // Light green for normal
                    },
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "%",
                fontSize = (9 * scaleFactor).sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF448866).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Range indicator in bottom corner - proportional sizing
        Text(
            text = if (isDynamicRange) {
                "$title ${animatedMinValue.toInt()}-${animatedMaxValue.toInt()}% AUTO"
            } else {
                "$title ${animatedMinValue.toInt()}-${animatedMaxValue.toInt()}%"
            },
            fontSize = (7 * scaleFactor).sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = if (isDynamicRange) {
                Color(0xFF44AA66).copy(alpha = 0.8f) // Brighter green for auto range
            } else {
                Color(0xFF336655).copy(alpha = 0.6f)
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (20 * scaleFactor).dp)
        )
    }
}

/**
 * Draws professional percentage gauge with green/orange color scheme
 */
private fun DrawScope.drawProfessionalPercentageGauge(
    value: Float,
    minValue: Float,
    maxValue: Float,
    isError: Boolean,
    isHighLoad: Boolean,
    scaleFactor: Float
) {
    val center = size.center
    val outerRadius = size.minDimension / 2 - 15.dp.toPx()

    // Draw professional bezel rings with green tint
    drawPercentageBezelRings(center, outerRadius, scaleFactor)

    // Draw high load warning zone (orange zone for values above 80%)
    drawHighLoadWarningZone(center, outerRadius, minValue, maxValue, scaleFactor)

    // Draw percentage tick marks
    drawPercentageTickMarks(center, outerRadius, minValue, maxValue, scaleFactor)

    // Draw the needle
    if (!isError) {
        drawProfessionalPercentageNeedle(
            center,
            outerRadius,
            value,
            minValue,
            maxValue,
            isHighLoad,
            scaleFactor
        )
    }

    // Draw center hub
    drawPercentageCenterHub(center, scaleFactor)
}

/**
 * Professional bezel rings with green metallic styling
 */
private fun DrawScope.drawPercentageBezelRings(
    center: Offset,
    radius: Float,
    scaleFactor: Float
) {
    // Outer ring with green metallic gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF66BB99), // Light green metallic
                Color(0xFF447755),
                Color(0xFF558866),
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
                Color(0xFF447755),
                Color(0xFF1A3A2A),
                Color(0xFF335544)
            )
        ),
        radius = radius + 12.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 2.dp.toPx() * scaleFactor)
    )
}

/**
 * High load warning zone (orange zone for values above 80%)
 */
private fun DrawScope.drawHighLoadWarningZone(
    center: Offset,
    radius: Float,
    minValue: Float,
    maxValue: Float,
    scaleFactor: Float
) {
    // Warning zone: 80% to 100%
    val warningStartValue = 80f
    val startAngle = 135f    // Bottom-left
    val totalSweep = 270f

    // Calculate angles for warning zone
    val warningStartAngle =
        startAngle + ((warningStartValue - minValue) / (maxValue - minValue)) * totalSweep
    val warningEndAngle = startAngle + totalSweep // End of gauge

    val sweepAngle = warningEndAngle - warningStartAngle

    // Draw orange warning zone
    val warningRect = androidx.compose.ui.geometry.Rect(
        center = center,
        radius = radius - 8.dp.toPx() * scaleFactor
    )

    // Outer orange glow
    drawArc(
        color = Color(0xFFFF8800).copy(alpha = 0.3f),
        startAngle = warningStartAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 16.dp.toPx() * scaleFactor),
        topLeft = warningRect.topLeft,
        size = warningRect.size
    )

    // Inner orange accent
    drawArc(
        color = Color(0xFFFFBB44).copy(alpha = 0.6f),
        startAngle = warningStartAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 3.dp.toPx() * scaleFactor),
        topLeft = warningRect.topLeft,
        size = warningRect.size
    )

    // Add "HIGH" text marker
    val midWarningAngle = warningStartAngle + (sweepAngle / 2f)
    val textRadius = radius - 35.dp.toPx() * scaleFactor
    val textAngle = Math.toRadians(midWarningAngle.toDouble())
    val textX = center.x + (textRadius * cos(textAngle)).toFloat()
    val textY = center.y + (textRadius * sin(textAngle)).toFloat()

    drawContext.canvas.nativeCanvas.drawText(
        "HIGH",
        textX,
        textY + 3.dp.toPx() * scaleFactor,
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, 255, 187, 68)
            textSize = 9.sp.toPx() * scaleFactor
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 0f, 0f, android.graphics.Color.argb(150, 0, 0, 0))
        }
    )
}

/**
 * Percentage tick marks with green color scheme
 */
private fun DrawScope.drawPercentageTickMarks(
    center: Offset,
    radius: Float,
    minValue: Float,
    maxValue: Float,
    scaleFactor: Float
) {
    val startAngle = 135f    // Bottom-left
    val totalSweep = 270f

    val majorTickLength = 16.dp.toPx() * scaleFactor
    val minorTickLength = 10.dp.toPx() * scaleFactor

    // Dynamic tick system based on range - like TemperatureGauge and PressureGauge
    val totalRange = maxValue - minValue

    // Determine appropriate tick intervals based on range
    val majorTickInterval = when {
        totalRange <= 10f -> 2f      // Every 2% for small ranges
        totalRange <= 25f -> 5f      // Every 5% for small-medium ranges
        totalRange <= 50f -> 10f     // Every 10% for medium ranges  
        totalRange <= 100f -> 20f    // Every 20% for larger ranges
        else -> 25f                  // Every 25% for very large ranges
    }

    val minorTickInterval = majorTickInterval / 2f

    val majorTickCount = (totalRange / majorTickInterval).toInt()
    val minorTickCount = (totalRange / minorTickInterval).toInt()

    // Draw minor ticks first
    for (i in 0..minorTickCount) {
        val tickValue = minValue + (i * minorTickInterval)
        if (tickValue > maxValue) break

        val progress = (tickValue - minValue) / totalRange
        val currentAngle = startAngle + (progress * totalSweep)
        val drawingAngle = if (currentAngle > 360f) currentAngle - 360f else currentAngle
        val angle = Math.toRadians(drawingAngle.toDouble())

        val isMajorTick = tickValue % majorTickInterval == 0f
        if (!isMajorTick) {
            val tickStart = Offset(
                center.x + ((radius - minorTickLength) * cos(angle)).toFloat(),
                center.y + ((radius - minorTickLength) * sin(angle)).toFloat()
            )
            val tickEnd = Offset(
                center.x + (radius * cos(angle)).toFloat(),
                center.y + (radius * sin(angle)).toFloat()
            )

            drawLine(
                color = Color(0xFF66BB99).copy(alpha = 0.5f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 1.5.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )
        }
    }

    // Draw major ticks
    for (i in 0..majorTickCount) {
        val tickValue = minValue + (i * majorTickInterval)
        if (tickValue > maxValue) break

        val progress = (tickValue - minValue) / totalRange
        val currentAngle = startAngle + (progress * totalSweep)
        val drawingAngle = if (currentAngle > 360f) currentAngle - 360f else currentAngle
        val angle = Math.toRadians(drawingAngle.toDouble())

        val tickStart = Offset(
            center.x + ((radius - majorTickLength) * cos(angle)).toFloat(),
            center.y + ((radius - majorTickLength) * sin(angle)).toFloat()
        )
        val tickEnd = Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )

        val isMin = tickValue == minValue
        val isMax = tickValue == maxValue
        val isZero = tickValue == 0f
        val isHighLevel = tickValue >= (maxValue * 0.8f)

        val tickColor = when {
            isHighLevel -> Color(0xFFFF8800) // Orange for high levels
            isMax -> Color(0xFF44AA66)       // Darker green for max
            else -> Color(0xFF88DDAA)        // Light green for normal
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

        val displayValue = if (tickValue % 1 == 0f) {
            tickValue.toInt().toString()
        } else {
            String.format("%.1f", tickValue)
        }

        drawContext.canvas.nativeCanvas.drawText(
            displayValue,
            numberX,
            numberY + 4.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = when {
                    isHighLevel -> android.graphics.Color.argb(255, 255, 136, 0)
                    isMax -> android.graphics.Color.argb(255, 68, 170, 102)
                    else -> android.graphics.Color.argb(255, 136, 221, 170)
                }
                textSize =
                    if (isMin || isMax || isZero) 16.sp.toPx() * scaleFactor else 14.sp.toPx() * scaleFactor
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(3f, 0f, 0f, android.graphics.Color.argb(120, 0, 0, 0))
            }
        )
    }

    // Always draw the maximum value tick if it wasn't included in the regular intervals
    val lastTickValue = minValue + (majorTickCount * majorTickInterval)
    if (lastTickValue < maxValue) {
        val progress = 1f // Always at the end of the gauge
        val currentAngle = startAngle + (progress * totalSweep)
        val drawingAngle = if (currentAngle > 360f) currentAngle - 360f else currentAngle
        val angle = Math.toRadians(drawingAngle.toDouble())

        val tickStart = Offset(
            center.x + ((radius - majorTickLength) * cos(angle)).toFloat(),
            center.y + ((radius - majorTickLength) * sin(angle)).toFloat()
        )
        val tickEnd = Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )

        val tickColor = Color(0xFF44AA66) // Darker green for max

        // Enhanced glow for max tick
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

        // Max value number
        val numberRadius = radius - 30.dp.toPx() * scaleFactor
        val numberX = center.x + (numberRadius * cos(angle)).toFloat()
        val numberY = center.y + (numberRadius * sin(angle)).toFloat()

        // Number background
        drawCircle(
            color = Color(0xFF000000).copy(alpha = 0.4f),
            radius = 10.dp.toPx() * scaleFactor,
            center = Offset(numberX, numberY - 2.dp.toPx() * scaleFactor)
        )

        val displayValue = if (maxValue % 1 == 0f) {
            maxValue.toInt().toString()
        } else {
            String.format("%.1f", maxValue)
        }

        drawContext.canvas.nativeCanvas.drawText(
            displayValue,
            numberX,
            numberY + 4.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(255, 68, 170, 102) // Max color
                textSize = 16.sp.toPx() * scaleFactor // Larger for max value
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(3f, 0f, 0f, android.graphics.Color.argb(120, 0, 0, 0))
            }
        )
    }
}

/**
 * Professional percentage needle with green/orange styling
 */
private fun DrawScope.drawProfessionalPercentageNeedle(
    center: Offset,
    radius: Float,
    value: Float,
    minValue: Float,
    maxValue: Float,
    isHighLoad: Boolean,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f
    val clampedValue = value.coerceIn(minValue, maxValue)

    val progress = (clampedValue - minValue) / (maxValue - minValue)
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
            color = if (isHighLoad) {
                Color(0xFFFF8800).copy(alpha = glowAlpha) // Orange glow for high load
            } else {
                Color(0xFF88DDAA).copy(alpha = glowAlpha) // Green glow for normal
            },
            start = center,
            end = needleTip,
            strokeWidth = glowWidth,
            cap = StrokeCap.Round
        )
    }

    // Main needle body
    drawLine(
        brush = if (isHighLoad) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFCC66), // Light orange
                    Color(0xFFFF8800), // Orange
                    Color(0xFFCC6600)  // Dark orange tip
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFAAFFDD), // Light green
                    Color(0xFF88DDAA), // Green
                    Color(0xFF66BB88)  // Dark green tip
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
        color = if (isHighLoad) {
            Color(0xFFFFDD88).copy(alpha = 0.9f)
        } else {
            Color(0xFFBBFFDD).copy(alpha = 0.9f)
        },
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 2.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )
}

/**
 * Professional center hub with green metallic styling
 */
private fun DrawScope.drawPercentageCenterHub(center: Offset, scaleFactor: Float) {
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF66BB99).copy(alpha = 0.6f),
                Color(0xFF334455).copy(alpha = 0.3f),
                Color.Transparent
            )
        ),
        radius = 16.dp.toPx() * scaleFactor,
        center = center
    )

    // Main hub with green metallic styling
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF66BB99),
                Color(0xFF447755),
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
                Color(0xFF889999),
                Color(0xFF667788)
            )
        ),
        radius = 6.dp.toPx() * scaleFactor,
        center = center
    )

    // Center point
    drawCircle(
        color = Color(0xFF66BB99),
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