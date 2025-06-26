package com.carsense.features.sensors.presentation.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
 * Realistic automotive RPM tachometer with dynamic range and enhanced visuals
 */
@Composable
fun RpmTachometer(
    modifier: Modifier = Modifier,
    sensorReading: SensorReading?,
    maxRpm: Float = 8000f
) {
    val currentRpm = sensorReading?.value?.toFloatOrNull() ?: 0f
    val isError = sensorReading?.isError ?: false
    val isLoading = sensorReading == null

    // Dynamic RPM range with smooth transitions and better thresholds
    var dynamicMaxRpm by remember { mutableFloatStateOf(maxRpm) }

    LaunchedEffect(currentRpm) {
        // Only increase range if we're consistently hitting high RPMs
        // More conservative thresholds to avoid unnecessary jumps
        if (currentRpm > dynamicMaxRpm * 0.95f) {
            val newMaxRpm = when {
                currentRpm > 11500f -> 12000f
                currentRpm > 9500f -> 10000f
                currentRpm > 7500f -> 8000f
                currentRpm > 5500f -> 6000f
                else -> dynamicMaxRpm
            }
            // Only update if it's a significant change
            if (newMaxRpm > dynamicMaxRpm) {
                dynamicMaxRpm = newMaxRpm
            }
        }
    }

    // Smooth animation for the max RPM range changes
    val animatedMaxRpm by animateFloatAsState(
        targetValue = dynamicMaxRpm,
        animationSpec = tween(durationMillis = 1500), // Smooth 1.5s transition
        label = "maxRpmAnimation"
    )

    // Smooth needle animation with custom interpolation for better feel
    val animatedRpm by animateFloatAsState(
        targetValue = if (isError || isLoading) 0f else currentRpm,
        animationSpec = tween(
            durationMillis = 150, // Back to original responsive timing
            easing = androidx.compose.animation.core.FastOutSlowInEasing // Smooth but responsive easing
        ),
        label = "rpmValue"
    )

    // Check if we're in redline for flashing effect
    val redlineThreshold = animatedMaxRpm * 0.85f
    val isInRedline = currentRpm >= redlineThreshold && !isError && !isLoading

    // Subtle redline flash animation
    val infiniteTransition = rememberInfiniteTransition(label = "redlineFlash")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600) // Slower, more subtle timing
        ),
        label = "flashAlpha"
    )

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

        // Ambient glow effect around the entire gauge
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val canvasScaleFactor = size.minDimension / 280.dp.toPx()

            // Subtle ambient lighting
            for (i in 1..3) {
                val glowRadius = size.minDimension / 2 - (i * 12 * canvasScaleFactor)
                drawCircle(
                    color = Color(0xFF4488BB).copy(alpha = 0.02f + (i * 0.008f)),
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
            drawProfessionalRpmTachometer(
                rpm = animatedRpm,
                maxRpm = animatedMaxRpm,
                isError = isError,
                isInRedline = isInRedline,
                flashAlpha = flashAlpha,
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
                    text = "${currentRpm.toInt()}",
                    fontSize = (20 * scaleFactor).sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = if (isInRedline) {
                        Color(0xFFFF6666).copy(alpha = flashAlpha) // Flash red when in redline
                    } else {
                        Color(0xFF99CCAA).copy(alpha = 0.9f) // Subtle green
                    },
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "RPM",
                fontSize = (9 * scaleFactor).sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF666666).copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Max range indicator in bottom corner - very subtle
        Text(
            text = "MAX ${animatedMaxRpm.toInt()}",
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
 * Draws professional RPM tachometer with subtle, refined styling
 */
private fun DrawScope.drawProfessionalRpmTachometer(
    rpm: Float,
    maxRpm: Float,
    isError: Boolean,
    isInRedline: Boolean,
    flashAlpha: Float,
    scaleFactor: Float
) {
    val center = size.center
    val outerRadius = size.minDimension / 2 - 15.dp.toPx()

    // Draw refined bezel rings
    drawProfessionalBezelRings(center, outerRadius, scaleFactor)

    // Draw enhanced tick marks
    drawProfessionalRpmTickMarks(center, outerRadius, maxRpm, scaleFactor)

    // Draw refined color zones (green/yellow/red only)
    drawIntegratedRpmZones(center, outerRadius, maxRpm, isInRedline, flashAlpha, scaleFactor)

    // Draw the needle with subtle glow
    if (!isError) {
        drawProfessionalRpmNeedle(center, outerRadius, rpm, maxRpm, scaleFactor)
    }

    // Draw modern center hub
    drawProfessionalCenterHub(center, scaleFactor)
}

/**
 * Professional bezel rings with refined gradients
 */
private fun DrawScope.drawProfessionalBezelRings(
    center: Offset,
    radius: Float,
    scaleFactor: Float
) {
    // Outer ring with professional metallic gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF777777),
                Color(0xFF333333),
                Color(0xFF555555),
                Color(0xFF222222)
            )
        ),
        radius = radius + 25.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 4.dp.toPx() * scaleFactor)
    )

    // Inner ring with subtle accent
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF333333),
                Color(0xFF1A1A1A),
                Color(0xFF222222)
            )
        ),
        radius = radius + 15.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 3.dp.toPx() * scaleFactor)
    )
}

/**
 * Professional tick marks with refined spacing
 */
private fun DrawScope.drawProfessionalRpmTickMarks(
    center: Offset,
    radius: Float,
    maxRpm: Float,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f
    val majorTickLength = 20.dp.toPx() * scaleFactor
    val minorTickLength = 12.dp.toPx() * scaleFactor
    val microTickLength = 6.dp.toPx() * scaleFactor

    // Calculate optimal tick spacing
    val majorIncrement = when {
        maxRpm > 10000f -> 2000f
        maxRpm > 8000f -> 1000f
        else -> 1000f
    }

    val minorIncrement = majorIncrement / 2f
    val microIncrement = majorIncrement / 10f

    // Draw micro ticks - very subtle
    val microTicks = (maxRpm / microIncrement).toInt()
    for (i in 0..microTicks) {
        val rpm = i * microIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * rpm / maxRpm)).toDouble())

        if (rpm % majorIncrement != 0f && rpm % minorIncrement != 0f) {
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
    val minorTicks = (maxRpm / minorIncrement).toInt()
    for (i in 0..minorTicks) {
        val rpm = i * minorIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * rpm / maxRpm)).toDouble())

        if (rpm % majorIncrement != 0f) {
            val tickStart = Offset(
                center.x + ((radius - minorTickLength) * cos(angle)).toFloat(),
                center.y + ((radius - minorTickLength) * sin(angle)).toFloat()
            )
            val tickEnd = Offset(
                center.x + (radius * cos(angle)).toFloat(),
                center.y + (radius * sin(angle)).toFloat()
            )

            drawLine(
                color = Color(0xFFAAAAAA).copy(alpha = 0.7f),
                start = tickStart,
                end = tickEnd,
                strokeWidth = 1.5.dp.toPx() * scaleFactor,
                cap = StrokeCap.Round
            )
        }
    }

    // Draw major ticks with numbers - professional styling
    val majorTicks = (maxRpm / majorIncrement).toInt()
    for (i in 0..majorTicks) {
        val rpm = i * majorIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * rpm / maxRpm)).toDouble())

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

        // RPM numbers - subtle and professional
        val numberRadius = radius - 25.dp.toPx() * scaleFactor
        val numberX = center.x + (numberRadius * cos(angle)).toFloat()
        val numberY = center.y + (numberRadius * sin(angle)).toFloat()

        val displayNumber = if (rpm >= 1000) "${(rpm / 1000).toInt()}" else "0"

        drawContext.canvas.nativeCanvas.drawText(
            displayNumber,
            numberX,
            numberY + 6.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color =
                    android.graphics.Color.argb(220, 255, 255, 255) // Slightly transparent white
                textSize = 18.sp.toPx() * scaleFactor
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
 * Professional needle with subtle glow
 */
private fun DrawScope.drawProfessionalRpmNeedle(
    center: Offset,
    radius: Float,
    rpm: Float,
    maxRpm: Float,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f
    val percentage = (rpm / maxRpm).coerceIn(0f, 1f)
    val needleAngle = Math.toRadians((startAngle + (totalSweep * percentage)).toDouble())
    val needleLength = radius - 35.dp.toPx() * scaleFactor

    val needleTip = Offset(
        center.x + (needleLength * cos(needleAngle)).toFloat(),
        center.y + (needleLength * sin(needleAngle)).toFloat()
    )

    // Subtle glow layers for the needle
    for (i in 1..4) {
        val glowWidth = (8 - i).dp.toPx() * scaleFactor
        val glowAlpha = 0.08f - (i * 0.015f)

        drawLine(
            color = Color(0xFF5599DD).copy(alpha = glowAlpha),
            start = center,
            end = needleTip,
            strokeWidth = glowWidth,
            cap = StrokeCap.Round
        )
    }

    // Main needle body - professional white/silver
    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFAFAFA),
                Color(0xFFE0E0E0),
                Color(0xFFBDBDBD)
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

    // Tip with very subtle glow
    drawLine(
        color = Color(0xFFFF5722).copy(alpha = 0.1f),
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 5.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )

    drawLine(
        color = Color(0xFFFF5722).copy(alpha = 0.9f),
        start = needleTipStart,
        end = needleTip,
        strokeWidth = 2.5.dp.toPx() * scaleFactor,
        cap = StrokeCap.Round
    )
}

/**
 * Professional center hub
 */
private fun DrawScope.drawProfessionalCenterHub(center: Offset, scaleFactor: Float) {
    // Subtle outer glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF444444).copy(alpha = 0.6f),
                Color(0xFF222222).copy(alpha = 0.3f),
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

    // Inner ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFCCCCCC),
                Color(0xFF999999),
                Color(0xFF666666)
            )
        ),
        radius = 8.dp.toPx() * scaleFactor,
        center = center
    )

    // Center point
    drawCircle(
        color = Color(0xFF888888),
        radius = 4.dp.toPx() * scaleFactor,
        center = center
    )

    // Subtle highlight
    drawCircle(
        color = Color(0xFFBBBBBB),
        radius = 1.5.dp.toPx() * scaleFactor,
        center = center.copy(
            x = center.x - 1.dp.toPx() * scaleFactor,
            y = center.y - 1.dp.toPx() * scaleFactor
        )
    )
}

/**
 * Integrated RPM zones that appear as part of the dial face
 */
private fun DrawScope.drawIntegratedRpmZones(
    center: Offset,
    radius: Float,
    maxRpm: Float,
    isInRedline: Boolean,
    flashAlpha: Float,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f

    // Calculate proportions: Green should be majority, yellow transition, red warning
    val redlineRpm = maxRpm * 0.85f // Red starts at 85% of max
    val yellowStartRpm = maxRpm * 0.75f // Yellow starts at 75% of max

    val greenPercent = yellowStartRpm / maxRpm // 0 to 75%
    val yellowPercent = (redlineRpm - yellowStartRpm) / maxRpm // 75% to 85%
    val redPercent = (maxRpm - redlineRpm) / maxRpm // 85% to 100%

    // Position zones to be truly integrated into the dial face
    val zoneRadius = radius - 25.dp.toPx() * scaleFactor // Deeper into gauge structure
    val zoneWidth = 6.dp.toPx() * scaleFactor // Even thinner for subtlety

    // Create gaps between zones to prevent overlapping
    val gapSize = 2f // 2 degrees gap between zones

    // Create recessed dial face background
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF1A1A1A),
                Color(0xFF0F0F0F),
                Color(0xFF050505)
            ),
            radius = zoneRadius + zoneWidth
        ),
        radius = zoneRadius + zoneWidth,
        center = center
    )

    // Draw subtle groove around the zones
    drawCircle(
        color = Color(0xFF000000).copy(alpha = 0.8f),
        radius = zoneRadius + zoneWidth + 1.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 1.dp.toPx() * scaleFactor)
    )

    drawCircle(
        color = Color(0xFF333333).copy(alpha = 0.4f),
        radius = zoneRadius - 1.dp.toPx() * scaleFactor,
        center = center,
        style = Stroke(width = 1.dp.toPx() * scaleFactor)
    )

    val gaugeSize = androidx.compose.ui.geometry.Size(zoneRadius * 2, zoneRadius * 2)
    val topLeft = Offset(center.x - zoneRadius, center.y - zoneRadius)

    // Green zone - embedded into dial face (0% to 75% with gap)
    val greenSweep = (totalSweep * greenPercent) - gapSize
    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color(0xFF1B5E20).copy(alpha = 0.9f), // Deeper, more embedded
                Color(0xFF2E7D32).copy(alpha = 0.95f),
                Color(0xFF388E3C).copy(alpha = 0.9f)
            ),
            center = center
        ),
        startAngle = startAngle,
        sweepAngle = greenSweep,
        useCenter = false,
        size = gaugeSize,
        topLeft = topLeft,
        style = Stroke(width = zoneWidth, cap = StrokeCap.Butt)
    )

    // Yellow zone - embedded amber (75% to 85% with gaps on both sides)
    val yellowStartAngle = startAngle + greenSweep + gapSize
    val yellowSweep = (totalSweep * yellowPercent) - gapSize
    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color(0xFFFBC02D).copy(alpha = 0.9f), // Pure yellow, no orange
                Color(0xFFFDD835).copy(alpha = 0.95f), // Pure bright yellow
                Color(0xFFFFEB3B).copy(alpha = 0.9f) // Pure yellow end
            ),
            center = center
        ),
        startAngle = yellowStartAngle,
        sweepAngle = yellowSweep,
        useCenter = false,
        size = gaugeSize,
        topLeft = topLeft,
        style = Stroke(width = zoneWidth, cap = StrokeCap.Butt)
    )

    // Red zone - with flashing effect when in redline (85% to 100% with gap)
    val redAlpha = if (isInRedline) flashAlpha else 0.9f
    val redIntensity = if (isInRedline) 1.2f else 1.0f
    val redStartAngle = yellowStartAngle + yellowSweep + gapSize
    val redSweep = (totalSweep * redPercent) - gapSize

    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color(0xFFB71C1C).copy(alpha = redAlpha * 0.9f), // Deep red
                Color(0xFFD32F2F).copy(alpha = redAlpha * redIntensity),
                Color(0xFFF44336).copy(alpha = redAlpha * 0.95f)
            ),
            center = center
        ),
        startAngle = redStartAngle,
        sweepAngle = redSweep,
        useCenter = false,
        size = gaugeSize,
        topLeft = topLeft,
        style = Stroke(width = zoneWidth, cap = StrokeCap.Butt)
    )

    // Add subtle flashing effect when in redline
    if (isInRedline) {
        // Single subtle glow - using same colors as red zone
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color(0xFFB71C1C).copy(alpha = flashAlpha * 0.6f), // Same deep red as zone
                    Color(0xFFD32F2F).copy(alpha = flashAlpha * 0.8f), // Same middle red as zone
                    Color(0xFFF44336).copy(alpha = flashAlpha * 0.6f)  // Same bright red as zone
                ),
                center = center
            ),
            startAngle = redStartAngle,
            sweepAngle = redSweep,
            useCenter = false,
            size = androidx.compose.ui.geometry.Size(
                (zoneRadius + 4.dp.toPx()) * 2,
                (zoneRadius + 4.dp.toPx()) * 2
            ),
            topLeft = Offset(
                center.x - zoneRadius - 4.dp.toPx(),
                center.y - zoneRadius - 4.dp.toPx()
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    // Subtle inner shadow for depth
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF000000).copy(alpha = 0.3f),
                Color(0xFF000000).copy(alpha = 0.6f)
            ),
            radius = zoneRadius - 2.dp.toPx()
        ),
        radius = zoneRadius - 2.dp.toPx(),
        center = center,
        style = Stroke(width = 3.dp.toPx())
    )

    // Add subtle RPM markings on the dial face
    drawSubtleRpmMarkings(center, radius - 45.dp.toPx() * scaleFactor, maxRpm, scaleFactor)
}

/**
 * Draw subtle RPM digit markings on the dial face
 */
private fun DrawScope.drawSubtleRpmMarkings(
    center: Offset,
    radius: Float,
    maxRpm: Float,
    scaleFactor: Float
) {
    val startAngle = 135f
    val totalSweep = 270f

    // Calculate optimal spacing for RPM markings
    val majorIncrement = when {
        maxRpm > 10000f -> 2000f
        maxRpm > 8000f -> 1000f
        else -> 1000f
    }

    val majorTicks = (maxRpm / majorIncrement).toInt()
    for (i in 0..majorTicks) {
        val rpm = i * majorIncrement
        val angle = Math.toRadians((startAngle + (totalSweep * rpm / maxRpm)).toDouble())

        val numberX = center.x + (radius * cos(angle)).toFloat()
        val numberY = center.y + (radius * sin(angle)).toFloat()

        val displayNumber = if (rpm >= 1000) "${(rpm / 1000).toInt()}" else "0"

        // Subtle RPM numbers
        drawContext.canvas.nativeCanvas.drawText(
            displayNumber,
            numberX,
            numberY + 4.dp.toPx() * scaleFactor,
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(160, 200, 200, 200) // Very subtle white
                textSize = 14.sp.toPx() * scaleFactor // Smaller than before
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT
                setShadowLayer(
                    2.dp.toPx() * scaleFactor,
                    0f,
                    0f,
                    android.graphics.Color.argb(60, 255, 255, 255)
                )
            }
        )
    }
} 