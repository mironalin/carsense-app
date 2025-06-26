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
 * Professional automotive pressure gauge for manifold pressure and timing advance
 * Yellow/orange color scheme with pressure display in kPa or degrees
 */
@Composable
fun PressureGauge(
    modifier: Modifier = Modifier,
    sensorReading: SensorReading?,
    title: String = "PRESSURE",
    minValue: Float = 0f,    // 0 kPa or 0°
    maxValue: Float = 100f,  // 100 kPa or 50° (timing advance)
    unit: String = "kPa",    // Unit: "kPa", "°", "g/s", etc.
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

    // Check if value is at high pressure/advance (above 80% of range)
    val isHighPressure = currentValue >= (animatedMaxValue * 0.8f) && !isError && !isLoading

    // Check if value is very low (below 20% of range)
    val isVeryLow = currentValue <= (animatedMaxValue * 0.2f) && !isError && !isLoading

    // Smooth value animation (matching TemperatureGauge)
    val animatedValue by animateFloatAsState(
        targetValue = if (isError || isLoading) animatedMinValue else currentValue,
        animationSpec = tween(durationMillis = 200),
        label = "pressureValue"
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
                        Color(0xFF332200),     // Dark yellow center
                        Color(0xFF443300),     // Darker yellow
                        Color(0xFF554400),     // Medium yellow
                        Color(0xFF443322),     // Dark yellow middle
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

        // Ambient glow effect with yellow/orange tint
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val canvasScaleFactor = size.minDimension / 280.dp.toPx()

            for (i in 1..3) {
                val glowRadius = size.minDimension / 2 - (i * 12 * canvasScaleFactor)
                drawCircle(
                    color = Color(0xFFDDAA44).copy(alpha = 0.02f + (i * 0.008f)),
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
            drawProfessionalPressureGauge(
                value = animatedValue,
                minValue = animatedMinValue,
                maxValue = animatedMaxValue,
                isError = isError,
                isHighPressure = isHighPressure,
                isVeryLow = isVeryLow,
                scaleFactor = canvasScaleFactor
            )
        }

        // Digital display with yellow/orange theme
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
                    fontSize = 18.sp * scaleFactor,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        isHighPressure -> Color(0xFFFF6644).copy(alpha = 0.95f) // Red for high pressure
                        currentValue <= 0f -> Color(0xFF4488FF).copy(alpha = 0.95f)    // Blue for vacuum/low pressure
                        else -> Color(0xFF88DDCC).copy(alpha = 0.9f)           // Cyan for normal
                    },
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = unit,
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
                "$title ${animatedMinValue.toInt()}-${animatedMaxValue.toInt()}$unit AUTO"
            } else {
                "$title ${animatedMinValue.toInt()}-${animatedMaxValue.toInt()}$unit"
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
 * Draws professional pressure gauge with yellow/orange color scheme
 */
private fun DrawScope.drawProfessionalPressureGauge(
    value: Float,
    minValue: Float,
    maxValue: Float,
    isError: Boolean,
    isHighPressure: Boolean,
    isVeryLow: Boolean,
    scaleFactor: Float
) {
    val center = size.center
    val outerRadius = size.minDimension / 2 - 15.dp.toPx()

    // Draw professional bezel rings with yellow tint
    drawPressureBezelRings(center, outerRadius, scaleFactor)

    // Draw pressure warning zones
    drawPressureWarningZones(center, outerRadius, minValue, maxValue, scaleFactor)

    // Draw pressure tick marks
    drawPressureTickMarks(center, outerRadius, minValue, maxValue, scaleFactor)

    // Draw the needle
    if (!isError) {
        drawProfessionalPressureNeedle(
            center,
            outerRadius,
            value,
            minValue,
            maxValue,
            isHighPressure,
            isVeryLow,
            scaleFactor
        )
    }

    // Draw center hub
    drawPressureCenterHub(center, scaleFactor)
}

/**
 * Professional bezel rings with yellow metallic styling
 */
private fun DrawScope.drawPressureBezelRings(center: Offset, radius: Float, scaleFactor: Float) {
    // Outer ring with yellow metallic gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFBB9966), // Light yellow metallic
                Color(0xFF997744),
                Color(0xFF998866),
                Color(0xFF553322)
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
                Color(0xFF997744),
                Color(0xFF3A2A1A),
                Color(0xFF665544)
            )
        ),
        radius = radius + 12.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 2.dp.toPx() * scaleFactor)
    )
}

/**
 * Pressure warning zones (high and low zones)
 */
private fun DrawScope.drawPressureWarningZones(
    center: Offset,
    radius: Float,
    minValue: Float,
    maxValue: Float,
    scaleFactor: Float
) {
    val startAngle = 135f    // Bottom-left
    val totalSweep = 270f

    // High pressure zone: 80% to 100% (orange warning)
    val highStartValue = maxValue * 0.8f
    val highStartAngle =
        startAngle + ((highStartValue - minValue) / (maxValue - minValue)) * totalSweep
    val highEndAngle = startAngle + totalSweep // End of gauge
    val highSweepAngle = highEndAngle - highStartAngle

    // Low pressure zone: 0% to 20% (blue warning)
    val lowEndValue = maxValue * 0.2f
    val lowStartAngle = startAngle
    val lowEndAngle = startAngle + ((lowEndValue - minValue) / (maxValue - minValue)) * totalSweep
    val lowSweepAngle = lowEndAngle - lowStartAngle

    val warningRect = androidx.compose.ui.geometry.Rect(
        center = center,
        radius = radius - 8.dp.toPx() * scaleFactor
    )

    // Draw high pressure zone (orange)
    if (highSweepAngle > 0) {
        drawArc(
            color = Color(0xFFFF8800).copy(alpha = 0.3f),
            startAngle = highStartAngle,
            sweepAngle = highSweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        drawArc(
            color = Color(0xFFFFAA44).copy(alpha = 0.6f),
            startAngle = highStartAngle,
            sweepAngle = highSweepAngle,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        // "HIGH" text marker
        val midHighAngle = highStartAngle + (highSweepAngle / 2f)
        val textRadius = radius - 35.dp.toPx() * scaleFactor
        val textAngle = Math.toRadians(midHighAngle.toDouble())
        val textX = center.x + (textRadius * cos(textAngle)).toFloat()
        val textY = center.y + (textRadius * sin(textAngle)).toFloat()

        drawContext.canvas.nativeCanvas.drawText(
            "HIGH",
            textX,
            textY + 3.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(255, 255, 170, 68)
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

    // Draw low pressure zone (blue)
    if (lowSweepAngle > 0) {
        drawArc(
            color = Color(0xFF4488FF).copy(alpha = 0.3f),
            startAngle = lowStartAngle,
            sweepAngle = lowSweepAngle,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        drawArc(
            color = Color(0xFF66AAFF).copy(alpha = 0.6f),
            startAngle = lowStartAngle,
            sweepAngle = lowSweepAngle,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx() * scaleFactor),
            topLeft = warningRect.topLeft,
            size = warningRect.size
        )

        // "LOW" text marker
        val midLowAngle = lowStartAngle + (lowSweepAngle / 2f)
        val textRadius = radius - 35.dp.toPx() * scaleFactor
        val textAngle = Math.toRadians(midLowAngle.toDouble())
        val textX = center.x + (textRadius * cos(textAngle)).toFloat()
        val textY = center.y + (textRadius * sin(textAngle)).toFloat()

        drawContext.canvas.nativeCanvas.drawText(
            "LOW",
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
 * Pressure tick marks with yellow/orange color scheme
 */
private fun DrawScope.drawPressureTickMarks(
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

    // Dynamic tick system based on range - like TemperatureGauge
    val totalRange = maxValue - minValue

    // Determine appropriate tick intervals based on range
    val majorTickInterval = when {
        totalRange <= 10f -> 2f      // Every 2 units for small ranges
        totalRange <= 50f -> 10f     // Every 10 units for medium ranges  
        totalRange <= 100f -> 20f    // Every 20 units for larger ranges
        totalRange <= 500f -> 50f    // Every 50 units for very large ranges
        else -> 100f                 // Every 100 units for huge ranges
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
                color = Color(0xFFBB9966).copy(alpha = 0.5f),
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
        val isHigh = tickValue >= (maxValue * 0.8f)
        val isLow = tickValue <= (maxValue * 0.2f)

        val tickColor = when {
            isHigh -> Color(0xFFFF8800)      // Orange for high
            isLow -> Color(0xFF4488FF)       // Blue for low
            isZero -> Color(0xFFDDCC88)      // Yellow for zero
            isMax -> Color(0xFF997744)       // Dark yellow for max
            else -> Color(0xFFCCBB77)        // Light yellow for normal
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

        // Value numbers for major ticks
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
                    isHigh -> android.graphics.Color.argb(255, 255, 136, 0)
                    isLow -> android.graphics.Color.argb(255, 68, 136, 255)
                    isZero -> android.graphics.Color.argb(255, 221, 204, 136)
                    isMax -> android.graphics.Color.argb(255, 153, 119, 68)
                    else -> android.graphics.Color.argb(255, 204, 187, 119)
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

        val isHigh = maxValue >= (maxValue * 0.8f) // Always true for max
        val tickColor = Color(0xFF997744) // Dark yellow for max

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
                color = android.graphics.Color.argb(255, 153, 119, 68) // Max color
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
 * Professional pressure needle with yellow/orange/blue styling
 */
private fun DrawScope.drawProfessionalPressureNeedle(
    center: Offset,
    radius: Float,
    value: Float,
    minValue: Float,
    maxValue: Float,
    isHighPressure: Boolean,
    isVeryLow: Boolean,
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
                isHighPressure -> Color(0xFFFF8800).copy(alpha = glowAlpha) // Orange glow for high pressure
                isVeryLow -> Color(0xFF4488FF).copy(alpha = glowAlpha)      // Blue glow for very low
                else -> Color(0xFFDDCC88).copy(alpha = glowAlpha)           // Yellow glow for normal
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
            isHighPressure -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFCC66), // Light orange
                    Color(0xFFFF8800), // Orange
                    Color(0xFFCC6600)  // Dark orange tip
                )
            )

            isVeryLow -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFFCCDDFF), // Light blue
                    Color(0xFF4488FF), // Blue
                    Color(0xFF3366CC)  // Dark blue tip
                )
            )

            else -> Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFEEAA), // Light yellow
                    Color(0xFFDDCC88), // Yellow
                    Color(0xFFBBAA66)  // Dark yellow tip
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
            isHighPressure -> Color(0xFFFFDD88).copy(alpha = 0.9f)
            isVeryLow -> Color(0xFFDDEEFF).copy(alpha = 0.9f)
            else -> Color(0xFFFFFFBB).copy(alpha = 0.9f)
        },
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 2.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )
}

/**
 * Professional center hub with yellow metallic styling
 */
private fun DrawScope.drawPressureCenterHub(center: Offset, scaleFactor: Float) {
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFBB9966).copy(alpha = 0.6f),
                Color(0xFF664433).copy(alpha = 0.3f),
                Color.Transparent
            )
        ),
        radius = 16.dp.toPx() * scaleFactor,
        center = center
    )

    // Main hub with yellow metallic styling
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFBB9966),
                Color(0xFF997744),
                Color(0xFF553322)
            )
        ),
        radius = 10.dp.toPx() * scaleFactor,
        center = center
    )

    // Inner ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFDDBBBB),
                Color(0xFFBB9999),
                Color(0xFF997777)
            )
        ),
        radius = 6.dp.toPx() * scaleFactor,
        center = center
    )

    // Center point
    drawCircle(
        color = Color(0xFFBB9966),
        radius = 3.dp.toPx() * scaleFactor,
        center = center
    )

    // Highlight
    drawCircle(
        color = Color(0xFFEEDDCC),
        radius = 1.dp.toPx() * scaleFactor,
        center = center.copy(
            x = center.x - 0.5.dp.toPx() * scaleFactor,
            y = center.y - 0.5.dp.toPx() * scaleFactor
        )
    )
} 