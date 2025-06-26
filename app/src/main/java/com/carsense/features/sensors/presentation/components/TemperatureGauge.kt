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
 * Professional automotive temperature gauge for intake air temperature
 * Purple/cyan color scheme with temperature display in Celsius
 */
@Composable
fun TemperatureGauge(
    modifier: Modifier = Modifier,
    sensorReading: SensorReading?,
    title: String = "TEMPERATURE",
    minValue: Float = -40f,  // -40°C (extreme cold)
    maxValue: Float = 80f,   // 80°C (very hot intake air)
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

    // Check if temperature is in warning zone (above 80% of max range)
    val isHighTemp = currentValue >= (animatedMaxValue * 0.8f) && !isError && !isLoading

    // Smooth value animation
    val animatedValue by animateFloatAsState(
        targetValue = if (isError || isLoading) animatedMinValue else currentValue,
        animationSpec = tween(durationMillis = 200),
        label = "temperatureValue"
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
                        Color(0xFF110033),     // Dark purple center
                        Color(0xFF220044),     // Darker purple
                        Color(0xFF330055),     // Medium purple
                        Color(0xFF221144),     // Dark purple middle
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

        // Ambient glow effect with purple/cyan tint
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
            drawProfessionalTemperatureGauge(
                value = animatedValue,
                minValue = animatedMinValue,
                maxValue = animatedMaxValue,
                isError = isError,
                isOverheating = isHighTemp,
                isVeryCold = currentValue <= -20f && !isError && !isLoading,
                scaleFactor = canvasScaleFactor
            )
        }

        // Digital display with purple/cyan theme
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 140.dp * scaleFactor)
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                Text(
                    text = "---",
                    fontSize = 18.sp * scaleFactor,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF66CCDD).copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            } else if (isError) {
                Text(
                    text = "ERR",
                    fontSize = 16.sp * scaleFactor,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFCC6666).copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "${animatedValue.toInt()}",
                    fontSize = 20.sp * scaleFactor,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        isHighTemp -> Color(0xFFFF6644).copy(alpha = 0.95f) // Red for overheating
                        currentValue <= -20f -> Color(0xFF4488FF).copy(alpha = 0.95f)    // Blue for very cold
                        else -> Color(0xFF88DDCC).copy(alpha = 0.9f)           // Cyan for normal
                    },
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "°C",
                fontSize = 9.sp * scaleFactor,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4488AA).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Range indicator in bottom corner
        Text(
            text = if (isDynamicRange) {
                "$title ${animatedMinValue.toInt()}-${animatedMaxValue.toInt()}°C AUTO"
            } else {
                "$title ${animatedMinValue.toInt()}-${animatedMaxValue.toInt()}°C"
            },
            fontSize = 7.sp * scaleFactor,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = if (isDynamicRange) {
                Color(0xFF4488BB).copy(alpha = 0.8f) // Brighter cyan for auto range
            } else {
                Color(0xFF336677).copy(alpha = 0.6f)
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp * scaleFactor)
        )
    }
}

/**
 * Draws professional temperature gauge with purple/cyan color scheme
 */
private fun DrawScope.drawProfessionalTemperatureGauge(
    value: Float,
    minValue: Float,
    maxValue: Float,
    isError: Boolean,
    isOverheating: Boolean,
    isVeryCold: Boolean,
    scaleFactor: Float
) {
    val center = size.center
    val outerRadius = size.minDimension / 2 - 15.dp.toPx()

    // Draw professional bezel rings with purple tint
    drawTemperatureBezelRings(center, outerRadius, scaleFactor)

    // Draw temperature warning zones
    drawTemperatureWarningZones(center, outerRadius, minValue, maxValue, scaleFactor)

    // Draw temperature tick marks
    drawTemperatureTickMarks(center, outerRadius, minValue, maxValue, scaleFactor)

    // Draw the needle
    if (!isError) {
        drawProfessionalTemperatureNeedle(
            center,
            outerRadius,
            value,
            minValue,
            maxValue,
            isOverheating,
            isVeryCold,
            scaleFactor
        )
    }

    // Draw center hub
    drawTemperatureCenterHub(center, scaleFactor)
}

/**
 * Professional bezel rings with purple metallic styling
 */
private fun DrawScope.drawTemperatureBezelRings(center: Offset, radius: Float, scaleFactor: Float) {
    // Outer ring with purple metallic gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF6699BB), // Light purple metallic
                Color(0xFF447799),
                Color(0xFF558899),
                Color(0xFF223355)
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
 * Temperature warning zones (hot and cold zones)
 */
private fun DrawScope.drawTemperatureWarningZones(
    center: Offset,
    radius: Float,
    minValue: Float,
    maxValue: Float,
    scaleFactor: Float
) {
    val startAngle = 135f    // Bottom-left
    val totalSweep = 270f

    // Hot zone: 80% of max range to max (red warning)
    val hotStartValue = maxValue * 0.8f
    val hotStartAngle =
        startAngle + ((hotStartValue - minValue) / (maxValue - minValue)) * totalSweep
    val hotEndAngle = startAngle + totalSweep // End of gauge
    val hotSweepAngle = hotEndAngle - hotStartAngle

    // Cold zone: -40°C to -20°C (blue warning)
    val coldEndValue = -20f
    val coldStartAngle = startAngle
    val coldEndAngle = startAngle + ((coldEndValue - minValue) / (maxValue - minValue)) * totalSweep
    val coldSweepAngle = coldEndAngle - coldStartAngle

    val warningRect = androidx.compose.ui.geometry.Rect(
        center = center,
        radius = radius - 8.dp.toPx() * scaleFactor
    )

    // Draw hot zone (red)
    if (hotSweepAngle > 0) {
        drawArc(
            color = Color(0xFFFF4444).copy(alpha = 0.3f),
            startAngle = hotStartAngle,
            sweepAngle = hotSweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        drawArc(
            color = Color(0xFFFF6666).copy(alpha = 0.6f),
            startAngle = hotStartAngle,
            sweepAngle = hotSweepAngle,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        // "HOT" text marker
        val midHotAngle = hotStartAngle + (hotSweepAngle / 2f)
        val textRadius = radius - 35.dp.toPx() * scaleFactor
        val textAngle = Math.toRadians(midHotAngle.toDouble())
        val textX = center.x + (textRadius * cos(textAngle)).toFloat()
        val textY = center.y + (textRadius * sin(textAngle)).toFloat()

        drawContext.canvas.nativeCanvas.drawText(
            "HOT",
            textX,
            textY + 3.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(255, 255, 102, 102)
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

    // Draw cold zone (blue)
    if (coldSweepAngle > 0) {
        drawArc(
            color = Color(0xFF4488FF).copy(alpha = 0.3f),
            startAngle = coldStartAngle,
            sweepAngle = coldSweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        drawArc(
            color = Color(0xFF66AAFF).copy(alpha = 0.6f),
            startAngle = coldStartAngle,
            sweepAngle = coldSweepAngle,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        // "COLD" text marker
        val midColdAngle = coldStartAngle + (coldSweepAngle / 2f)
        val textRadius = radius - 35.dp.toPx() * scaleFactor
        val textAngle = Math.toRadians(midColdAngle.toDouble())
        val textX = center.x + (textRadius * cos(textAngle)).toFloat()
        val textY = center.y + (textRadius * sin(textAngle)).toFloat()

        drawContext.canvas.nativeCanvas.drawText(
            "COLD",
            textX,
            textY + 3.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(255, 102, 170, 255)
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
}

/**
 * Temperature tick marks with purple/cyan color scheme
 */
private fun DrawScope.drawTemperatureTickMarks(
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

    // Temperature range: -40°C to 80°C = 120°C total
    // Major ticks every 20°C: -40, -20, 0, 20, 40, 60, 80
    val majorTickInterval = 20f
    val minorTickInterval = 10f

    val totalRange = maxValue - minValue
    val majorTickCount = (totalRange / majorTickInterval).toInt()
    val minorTickCount = (totalRange / minorTickInterval).toInt()

    // Draw minor ticks first
    for (i in 0..minorTickCount) {
        val temperature = minValue + (i * minorTickInterval)
        val progress = (temperature - minValue) / totalRange
        val currentAngle = startAngle + (progress * totalSweep)
        val drawingAngle = if (currentAngle > 360f) currentAngle - 360f else currentAngle
        val angle = Math.toRadians(drawingAngle.toDouble())

        val isMajorTick = temperature % majorTickInterval == 0f
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
                color = Color(0xFF6699BB).copy(alpha = 0.5f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 1.5.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )
        }
    }

    // Draw major ticks
    for (i in 0..majorTickCount) {
        val temperature = minValue + (i * majorTickInterval)
        val progress = (temperature - minValue) / totalRange
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

        val isMin = temperature == minValue
        val isMax = temperature == maxValue
        val isZero = temperature == 0f
        val isHot = temperature >= (maxValue * 0.8f)
        val isCold = temperature <= -20f

        val tickColor = when {
            isHot -> Color(0xFFFF4444)      // Red for hot
            isCold -> Color(0xFF4488FF)     // Blue for cold
            isZero -> Color(0xFF88DDCC)     // Cyan for zero
            isMax -> Color(0xFF4477AA)      // Purple for max
            else -> Color(0xFF88DDAA)       // Light cyan for normal
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

        // Temperature numbers for major ticks
        val numberRadius = radius - 30.dp.toPx() * scaleFactor
        val numberX = center.x + (numberRadius * cos(angle)).toFloat()
        val numberY = center.y + (numberRadius * sin(angle)).toFloat()

        drawContext.canvas.nativeCanvas.drawText(
            temperature.toInt().toString(),
            numberX,
            numberY + 4.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = when {
                    isHot -> android.graphics.Color.argb(255, 255, 68, 68)
                    isCold -> android.graphics.Color.argb(255, 68, 136, 255)
                    isZero -> android.graphics.Color.argb(255, 136, 221, 204)
                    isMax -> android.graphics.Color.argb(255, 68, 119, 170)
                    else -> android.graphics.Color.argb(255, 136, 221, 170)
                }
                textSize =
                    if (isMin || isMax || isZero) 16.sp.toPx() * scaleFactor else 14.sp.toPx() * scaleFactor
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

        val isHot = maxValue >= (maxValue * 0.8f) // Always true for max
        val tickColor =
            if (isHot) Color(0xFFFF4444) else Color(0xFF4477AA) // Red for hot, purple for max

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

        // Max temperature number
        val numberRadius = radius - 30.dp.toPx() * scaleFactor
        val numberX = center.x + (numberRadius * cos(angle)).toFloat()
        val numberY = center.y + (numberRadius * sin(angle)).toFloat()

        drawContext.canvas.nativeCanvas.drawText(
            maxValue.toInt().toString(),
            numberX,
            numberY + 4.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = if (isHot) {
                    android.graphics.Color.argb(255, 255, 68, 68) // Red for hot
                } else {
                    android.graphics.Color.argb(255, 68, 119, 170) // Purple for max
                }
                textSize = 16.sp.toPx() * scaleFactor // Larger for max value
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
    }
}

/**
 * Professional temperature needle with purple/cyan/red styling
 */
private fun DrawScope.drawProfessionalTemperatureNeedle(
    center: Offset,
    radius: Float,
    value: Float,
    minValue: Float,
    maxValue: Float,
    isOverheating: Boolean,
    isVeryCold: Boolean,
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
        val glowWidth = ((6 - i).dp.toPx() * scaleFactor)
        val glowAlpha = 0.1f - (i * 0.025f)

        drawLine(
            color = when {
                isOverheating -> Color(0xFFFF4444).copy(alpha = glowAlpha) // Red glow for overheating
                isVeryCold -> Color(0xFF4488FF).copy(alpha = glowAlpha)    // Blue glow for very cold
                else -> Color(0xFF88DDCC).copy(alpha = glowAlpha)          // Cyan glow for normal
            },
            start = center,
            end = needleTip,
            strokeWidth = glowWidth,
            cap = StrokeCap.Round
        )
    }

    // Main needle body
    drawLine(
        brush = when {
            isOverheating -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFCC66), // Light red
                    Color(0xFFFF4444), // Red
                    Color(0xFFCC3333)  // Dark red tip
                )
            )

            isVeryCold -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFFCCDDFF), // Light blue
                    Color(0xFF4488FF), // Blue
                    Color(0xFF3366CC)  // Dark blue tip
                )
            )

            else -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFFAAFFDD), // Light cyan
                    Color(0xFF88DDCC), // Cyan
                    Color(0xFF66BBAA)  // Dark cyan tip
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
        color = when {
            isOverheating -> Color(0xFFFFDD88).copy(alpha = 0.9f)
            isVeryCold -> Color(0xFFDDEEFF).copy(alpha = 0.9f)
            else -> Color(0xFFBBFFDD).copy(alpha = 0.9f)
        },
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 2.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )
}

/**
 * Professional center hub with purple metallic styling
 */
private fun DrawScope.drawTemperatureCenterHub(center: Offset, scaleFactor: Float) {
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF6699BB).copy(alpha = 0.6f),
                Color(0xFF334466).copy(alpha = 0.3f),
                Color.Transparent
            )
        ),
        radius = 16.dp.toPx() * scaleFactor,
        center = center
    )

    // Main hub with purple metallic styling
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF6699BB),
                Color(0xFF447799),
                Color(0xFF223355)
            )
        ),
        radius = 10.dp.toPx() * scaleFactor,
        center = center
    )

    // Inner ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFAABBDD),
                Color(0xFF8899BB),
                Color(0xFF667799)
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