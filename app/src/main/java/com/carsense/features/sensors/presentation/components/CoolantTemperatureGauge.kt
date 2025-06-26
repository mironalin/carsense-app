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
 * Professional automotive coolant temperature gauge with 90°C optimal positioning
 * Matches the clean monochromatic style of RPM and Speed gauges
 */
@Composable
fun CoolantTemperatureGauge(
    modifier: Modifier = Modifier,
    sensorReading: SensorReading?,
    minTemp: Float = 50f,
    maxTemp: Float = 130f // 130°C upper limit
) {
    val currentTemp = sensorReading?.value?.toFloatOrNull() ?: minTemp
    val isError = sensorReading?.isError ?: false
    val isLoading = sensorReading == null

    // Check if temperature is in dangerous zone (above 120°C)
    val isDangerous = currentTemp >= 120f && !isError && !isLoading

    // Smooth temperature animation
    val animatedTemp by animateFloatAsState(
        targetValue = if (isError || isLoading) minTemp else currentTemp,
        animationSpec = tween(
            durationMillis = 150, // Responsive timing matching other gauges
            easing = FastOutSlowInEasing
        ),
        label = "temperatureValue"
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f) // Ensure perfect circle
            .shadow(20.dp, CircleShape) // Deep shadow matching other gauges
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
                    radius = 350f
                )
            )
    ) {
        // Calculate scale factor based on actual size vs baseline (280dp)
        val scaleFactor = with(LocalDensity.current) {
            minOf(maxWidth.toPx(), maxHeight.toPx()) / 280.dp.toPx()
        }

        // Ambient glow effect - neutral like other gauges
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val canvasScaleFactor = size.minDimension / 280.dp.toPx()

            // Subtle ambient lighting - neutral thermal tint
            for (i in 1..3) {
                val glowRadius = size.minDimension / 2 - (i * 12 * canvasScaleFactor)
                drawCircle(
                    color = Color(0xFF888888).copy(alpha = 0.012f + (i * 0.004f)), // Neutral gray
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
            drawProfessionalCoolantGauge(
                temperature = animatedTemp,
                minTemp = minTemp,
                maxTemp = maxTemp,
                isError = isError,
                isDangerous = isDangerous,
                scaleFactor = canvasScaleFactor
            )
        }

        // Digital display positioned to avoid overlap
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = (130 * scaleFactor).dp) // Positioned for smaller gauge
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
                    text = "${currentTemp.toInt()}",
                    fontSize = (20 * scaleFactor).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = if (isDangerous) {
                        Color(0xFFFF4444).copy(alpha = 0.95f) // Bright red for dangerous temperatures
                    } else {
                        Color(0xFFAAAAAA).copy(alpha = 0.9f) // Neutral color like other gauges
                    },
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "°C",
                fontSize = (9 * scaleFactor).sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF666666).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Temperature range indicator in bottom corner
        Text(
            text = "COOLANT ${minTemp.toInt()}-${maxTemp.toInt()}°C",
            fontSize = (7 * scaleFactor).sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF555555).copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (20 * scaleFactor).dp)
        )
    }
}

/**
 * Draws professional coolant temperature gauge matching RPM/Speed gauge style
 * with automotive positioning (90°C at 12 o'clock) and danger zone indicators
 */
private fun DrawScope.drawProfessionalCoolantGauge(
    temperature: Float,
    minTemp: Float,
    maxTemp: Float,
    isError: Boolean,
    isDangerous: Boolean,
    scaleFactor: Float
) {
    val center = size.center
    val outerRadius = size.minDimension / 2 - 15.dp.toPx()

    // Draw professional bezel rings
    drawProfessionalBezelRings(center, outerRadius, scaleFactor)

    // Draw optimal zone indicator (green zone around 90°C)
    drawOptimalZoneIndicator(center, outerRadius, minTemp, maxTemp, scaleFactor)

    // Draw danger zone indicator (red zone for temperatures above 120°C)
    drawDangerZoneIndicator(center, outerRadius, minTemp, maxTemp, scaleFactor)

    // Draw temperature tick marks with 90°C at top (automotive standard)
    drawAutomotiveTemperatureTickMarks(center, outerRadius, minTemp, maxTemp, scaleFactor)

    // Draw the needle
    if (!isError) {
        drawProfessionalTemperatureNeedle(
            center,
            outerRadius,
            temperature,
            minTemp,
            maxTemp,
            isDangerous,
            scaleFactor
        )
    }

    // Draw center hub
    drawProfessionalCenterHub(center, scaleFactor)
}

/**
 * Professional bezel rings matching other gauges
 */
private fun DrawScope.drawProfessionalBezelRings(
    center: Offset,
    radius: Float,
    scaleFactor: Float
) {
    // Outer ring with metallic gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF999999), // Light metallic
                Color(0xFF555555),
                Color(0xFF777777),
                Color(0xFF333333)
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
                Color(0xFF555555),
                Color(0xFF2A2A2A),
                Color(0xFF444444)
            )
        ),
        radius = radius + 12.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 2.dp.toPx() * scaleFactor)
    )
}

/**
 * Temperature tick marks with 90°C positioned at 12 o'clock (automotive standard)
 * Following the same clean style as RPM/Speed gauges
 */
private fun DrawScope.drawAutomotiveTemperatureTickMarks(
    center: Offset,
    radius: Float,
    minTemp: Float,
    maxTemp: Float,
    scaleFactor: Float
) {
    // Automotive coolant gauge logic: 50°C to 90°C takes half the arc, 90°C to 130°C takes the other half
    // Position limits lower like traditional automotive gauges
    val startAngle = 175f    // Bottom-left (7:30 position) - lower positioning
    val optimalAngle = 270f  // Top (12 o'clock) - 90°C optimal
    val endAngle = 365f      // Bottom-right (4:30 position) - lower positioning
    // Total sweep: 90° (225° to 315°)

    val majorTickLength = 16.dp.toPx() * scaleFactor
    val minorTickLength = 10.dp.toPx() * scaleFactor

    // Draw more incremental lines between main points for better visual reference
    val totalTicks = 16 // Creates 17 sections total (including main points) - more tick marks

    for (i in 0..totalTicks) {
        val progress = i.toFloat() / totalTicks.toFloat()

        // Calculate angle - split the arc in half at 90°C
        val currentAngle = if (progress <= 0.5f) {
            // First half: from startAngle to optimalAngle (50°C to 90°C)
            val halfProgress = progress / 0.5f
            startAngle + (halfProgress * (optimalAngle - startAngle))
        } else {
            // Second half: from optimalAngle to endAngle (90°C to 130°C)
            val halfProgress = (progress - 0.5f) / 0.5f
            optimalAngle + (halfProgress * (endAngle - optimalAngle))
        }

        val angle = Math.toRadians(currentAngle.toDouble())

        // Determine if this is a major tick (main temperature points)
        val isMajorTick = when (i) {
            0 -> true                    // minTemp (bottom-left)
            totalTicks / 2 -> true       // 90°C (top)
            totalTicks -> true           // maxTemp (bottom-right)
            else -> false                // minor tick
        }

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
            // Major tick for main temperature points
            val isOptimal = i == totalTicks / 2 // middle position (90°C)
            val isMinTemp = i == 0
            val isMaxTemp = i == totalTicks

            val tickColor = when {
                isOptimal -> Color(0xFF4CAF50) // Green for optimal
                isMaxTemp -> Color(0xFFFF4444) // Red for max temp
                else -> Color(0xFFDDDDDD) // White for min temp
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
                strokeWidth = if (isOptimal) 8.dp.toPx() * scaleFactor else 6.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )

            // Main tick line with gradient
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        tickColor.copy(alpha = 0.8f),
                        tickColor,
                        tickColor.copy(alpha = 0.9f)
                    )
                ),
                start = tickStart,
                end = tickEnd,
                strokeWidth = if (isOptimal) 4.dp.toPx() * scaleFactor else 3.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )

            // Temperature numbers - enhanced styling
            val numberRadius = radius - 30.dp.toPx() * scaleFactor
            val numberX = center.x + (numberRadius * cos(angle)).toFloat()
            val numberY = center.y + (numberRadius * sin(angle)).toFloat()

            val temp = when (i) {
                0 -> minTemp.toInt()           // Start temp (left side)
                totalTicks / 2 -> 90           // 90°C (optimal, top)
                totalTicks -> maxTemp.toInt()  // Max temp (right side)
                else -> 0
            }

            // Number background for better readability
            drawCircle(
                color = Color(0xFF000000).copy(alpha = 0.3f),
                radius = 12.dp.toPx() * scaleFactor,
                center = Offset(numberX, numberY - 2.dp.toPx() * scaleFactor)
            )

            drawContext.canvas.nativeCanvas.drawText(
                temp.toString(),
                numberX,
                numberY + 5.dp.toPx() * scaleFactor,
                android.graphics.Paint().apply {
                    color = when {
                        isOptimal -> android.graphics.Color.argb(
                            255,
                            76,
                            175,
                            80
                        ) // Green for optimal
                        isMaxTemp -> android.graphics.Color.argb(255, 255, 68, 68) // Red for max
                        else -> android.graphics.Color.argb(255, 255, 255, 255) // White for min
                    }
                    textSize =
                        if (isOptimal) 18.sp.toPx() * scaleFactor else 16.sp.toPx() * scaleFactor
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
            // Enhanced minor tick - gradient effect
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFAAAAAA).copy(alpha = 0.3f),
                        Color(0xFFCCCCCC).copy(alpha = 0.7f),
                        Color(0xFFAAAAAA).copy(alpha = 0.3f)
                    )
                ),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Professional temperature needle matching other gauges
 */
private fun DrawScope.drawProfessionalTemperatureNeedle(
    center: Offset,
    radius: Float,
    temperature: Float,
    minTemp: Float,
    maxTemp: Float,
    isDangerous: Boolean,
    scaleFactor: Float
) {
    // Automotive coolant gauge needle positioning
    // 50°C to 90°C takes half the arc, 90°C to 130°C takes the other half
    // Position limits lower like traditional automotive gauges
    val startAngle = 175f    // Bottom-left (7:30 position) - lower positioning
    val optimalAngle = 270f  // Top (12 o'clock) - 90°C optimal  
    val endAngle = 365f      // Bottom-right (4:30 position) - lower positioning

    val clampedTemp = temperature.coerceIn(minTemp, maxTemp)

    // Calculate needle angle using automotive logic
    val needleAngleDegrees = if (clampedTemp <= 90f) {
        // First half: 50°C to 90°C uses first half of the arc
        val progress = (clampedTemp - minTemp) / (90f - minTemp)
        startAngle + (progress * (optimalAngle - startAngle))
    } else {
        // Second half: 90°C to 130°C uses second half of the arc  
        val progress = (clampedTemp - 90f) / (maxTemp - 90f)
        optimalAngle + (progress * (endAngle - optimalAngle))
    }

    val needleAngle = Math.toRadians(needleAngleDegrees.toDouble())
    val needleLength = radius - 25.dp.toPx()

    val needleTip = Offset(
        center.x + (needleLength * cos(needleAngle)).toFloat(),
        center.y + (needleLength * sin(needleAngle)).toFloat()
    )

    // Professional needle glow layers
    for (i in 1..3) {
        val glowWidth = (6 - i).dp.toPx()
        val glowAlpha = 0.1f - (i * 0.025f)

        drawLine(
            color = if (isDangerous) {
                Color(0xFFFF4444).copy(alpha = glowAlpha) // Red glow for dangerous temperatures
            } else {
                Color(0xFFFFFFFF).copy(alpha = glowAlpha) // White glow for normal temperatures
            },
            start = center,
            end = needleTip,
            strokeWidth = glowWidth,
            cap = StrokeCap.Round
        )
    }

    // Main needle body - red when dangerous, white when normal
    drawLine(
        brush = if (isDangerous) {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF6666), // Light red
                    Color(0xFFFF4444), // Red
                    Color(0xFFCC3333)  // Dark red tip
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFFFF8), // Warm white
                    Color(0xFFFFFFFF), // Pure white
                    Color(0xFFEEEEEE)  // Light gray tip
                )
            )
        },
        start = center,
        end = needleTip,
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Needle tip highlight
    val tipLength = 20.dp.toPx()
    val needleTipStart = Offset(
        center.x + ((needleLength - tipLength) * cos(needleAngle)).toFloat(),
        center.y + ((needleLength - tipLength) * sin(needleAngle)).toFloat()
    )

    drawLine(
        color = if (isDangerous) {
            Color(0xFFFF8888).copy(alpha = 0.9f) // Light red highlight for dangerous
        } else {
            Color(0xFFFFFFFF).copy(alpha = 0.9f) // White highlight for normal
        },
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
    )
}

/**
 * Professional center hub matching other gauges
 */
private fun DrawScope.drawProfessionalCenterHub(center: Offset, scaleFactor: Float) {
    // Outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF888888).copy(alpha = 0.6f),
                Color(0xFF444444).copy(alpha = 0.3f),
                Color.Transparent
            )
        ),
        radius = 16.dp.toPx(),
        center = center
    )

    // Main hub with metallic styling
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF999999),
                Color(0xFF555555),
                Color(0xFF333333)
            )
        ),
        radius = 10.dp.toPx(),
        center = center
    )

    // Inner ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFDDDDDD),
                Color(0xFFBBBBBB),
                Color(0xFF999999)
            )
        ),
        radius = 6.dp.toPx(),
        center = center
    )

    // Center point
    drawCircle(
        color = Color(0xFF888888),
        radius = 3.dp.toPx(),
        center = center
    )

    // Highlight
    drawCircle(
        color = Color(0xFFFFFFFF),
        radius = 1.dp.toPx(),
        center = center.copy(
            x = center.x - 0.5.dp.toPx(),
            y = center.y - 0.5.dp.toPx()
        )
    )
}

/**
 * Draw danger zone indicator (red zone for temperatures above 120°C)
 * Enhanced red zone similar to RPM gauge redline - more prominent
 */
private fun DrawScope.drawDangerZoneIndicator(
    center: Offset,
    radius: Float,
    minTemp: Float,
    maxTemp: Float,
    scaleFactor: Float
) {
    // Calculate the danger zone arc (120°C to 130°C)
    val dangerStartTemp = 120f
    val startAngle = 175f    // Bottom-left (7:30 position) - lower positioning
    val optimalAngle = 270f  // Top (12 o'clock) - 90°C optimal
    val endAngle = 365f      // Bottom-right (4:30 position) - lower positioning

    // Calculate angle for 120°C (start of danger zone)
    val dangerStartAngle = if (dangerStartTemp <= 90f) {
        // First half: 50°C to 90°C uses first half of the arc
        val progress = (dangerStartTemp - minTemp) / (90f - minTemp)
        startAngle + (progress * (optimalAngle - startAngle))
    } else {
        // Second half: 90°C to maxTemp uses second half of the arc
        val progress = (dangerStartTemp - 90f) / (maxTemp - 90f)
        optimalAngle + (progress * (endAngle - optimalAngle))
    }

    // Danger zone goes from 120°C to maxTemp (130°C)
    val dangerEndAngle = endAngle

    // Calculate sweep angle for the danger zone
    val sweepAngle = dangerEndAngle - dangerStartAngle

    // Draw multiple layers for more prominent red zone
    val baseRadius = radius - 5.dp.toPx()

    // Outer glow layer for better visibility
    val outerRect = androidx.compose.ui.geometry.Rect(
        center = center,
        radius = baseRadius + 8.dp.toPx()
    )
    drawArc(
        color = Color(0xFFFF1744).copy(alpha = 0.4f),
        startAngle = dangerStartAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 20.dp.toPx()),
        topLeft = outerRect.topLeft,
        size = outerRect.size
    )

    // Main red zone - thicker and more prominent
    val mainRect = androidx.compose.ui.geometry.Rect(
        center = center,
        radius = baseRadius
    )
    drawArc(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFB71C1C), // Deep red (inner)
                Color(0xFFD32F2F), // Middle red
                Color(0xFFF44336), // Bright red
                Color(0xFFFF1744)  // Very bright red (outer)
            ),
            radius = 60.dp.toPx()
        ),
        startAngle = dangerStartAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 18.dp.toPx()),
        topLeft = mainRect.topLeft,
        size = mainRect.size
    )

    // Inner bright accent line for definition
    drawArc(
        color = Color(0xFFFFEBEE).copy(alpha = 0.9f), // Very light red accent
        startAngle = dangerStartAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 3.dp.toPx()),
        topLeft = mainRect.topLeft,
        size = mainRect.size
    )

    // Add "DANGER" text markers at the red zone
    val midDangerAngle = dangerStartAngle + (sweepAngle / 2f)
    val textRadius = radius - 35.dp.toPx()
    val textAngle = Math.toRadians(midDangerAngle.toDouble())
    val textX = center.x + (textRadius * cos(textAngle)).toFloat()
    val textY = center.y + (textRadius * sin(textAngle)).toFloat()

    drawContext.canvas.nativeCanvas.drawText(
        "HOT",
        textX,
        textY + 3.dp.toPx(),
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, 255, 255, 255)
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 0f, 0f, android.graphics.Color.argb(150, 0, 0, 0))
        }
    )
}

/**
 * Draw optimal zone indicator (subtle green zone around 90°C)
 */
private fun DrawScope.drawOptimalZoneIndicator(
    center: Offset,
    radius: Float,
    minTemp: Float,
    maxTemp: Float,
    scaleFactor: Float
) {
    // Optimal zone: 85°C to 95°C (around the 90°C optimal point)
    val optimalStartTemp = 85f
    val optimalEndTemp = 95f
    val startAngle = 175f    // Bottom-left (7:30 position) - lower positioning
    val optimalAngle = 270f  // Top (12 o'clock) - 90°C optimal
    val endAngle = 365f      // Bottom-right (4:30 position) - lower positioning

    // Calculate angles for optimal zone
    val optimalStartAngle = if (optimalStartTemp <= 90f) {
        val progress = (optimalStartTemp - minTemp) / (90f - minTemp)
        startAngle + (progress * (optimalAngle - startAngle))
    } else {
        val progress = (optimalStartTemp - 90f) / (maxTemp - 90f)
        optimalAngle + (progress * (endAngle - optimalAngle))
    }

    val optimalEndAngle = if (optimalEndTemp <= 90f) {
        val progress = (optimalEndTemp - minTemp) / (90f - minTemp)
        startAngle + (progress * (optimalAngle - startAngle))
    } else {
        val progress = (optimalEndTemp - 90f) / (maxTemp - 90f)
        optimalAngle + (progress * (endAngle - optimalAngle))
    }

    val sweepAngle = optimalEndAngle - optimalStartAngle

    // Draw subtle green optimal zone
    val optimalRect = androidx.compose.ui.geometry.Rect(
        center = center,
        radius = radius - 12.dp.toPx()
    )

    drawArc(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF2E7D32).copy(alpha = 0.3f), // Deep green
                Color(0xFF388E3C).copy(alpha = 0.4f), // Medium green
                Color(0xFF4CAF50).copy(alpha = 0.3f)  // Bright green
            ),
            radius = 40.dp.toPx()
        ),
        startAngle = optimalStartAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 12.dp.toPx()),
        topLeft = optimalRect.topLeft,
        size = optimalRect.size
    )

    // Inner green accent
    drawArc(
        color = Color(0xFFC8E6C9).copy(alpha = 0.6f), // Light green accent
        startAngle = optimalStartAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = 2.dp.toPx()),
        topLeft = optimalRect.topLeft,
        size = optimalRect.size
    )
} 