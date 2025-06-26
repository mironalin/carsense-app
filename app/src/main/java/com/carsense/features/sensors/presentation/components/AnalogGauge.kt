package com.carsense.features.sensors.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carsense.features.sensors.domain.model.SensorReading
import kotlin.math.cos
import kotlin.math.sin

/**
 * Analog gauge component that displays sensor readings in a circular gauge format.
 */
@Composable
fun AnalogGauge(
    modifier: Modifier = Modifier,
    title: String,
    sensorReading: SensorReading?,
    minValue: Float = 0f,
    maxValue: Float = 100f
) {
    val currentValue = sensorReading?.value?.toFloatOrNull() ?: 0f
    val unit = sensorReading?.unit ?: ""
    val isError = sensorReading?.isError ?: false
    val isLoading = sensorReading == null

    // Animate the gauge needle position
    val animatedValue by animateFloatAsState(
        targetValue = if (isError || isLoading) 0f else currentValue,
        animationSpec = tween(durationMillis = 500),
        label = "gaugeValue"
    )

    // Calculate the percentage for the needle position
    val percentage = if (maxValue > minValue) {
        ((animatedValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            // Calculate scale factor based on actual size vs baseline (200dp)
            val scaleFactor = with(LocalDensity.current) {
                minOf(maxWidth.toPx(), maxHeight.toPx()) / 200.dp.toPx()
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding((16 * scaleFactor).dp)
            ) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    fontSize = (14 * scaleFactor).sp
                )

                // Gauge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size((120 * scaleFactor).dp)
                        .weight(1f)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val backgroundColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    val errorColor = MaterialTheme.colorScheme.error

                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val canvasScaleFactor = size.minDimension / 200.dp.toPx()
                        drawGauge(
                            percentage = percentage,
                            primaryColor = primaryColor,
                            backgroundColor = backgroundColor,
                            isError = isError,
                            errorColor = errorColor,
                            scaleFactor = canvasScaleFactor
                        )
                    }

                    // Center dot
                    Canvas(
                        modifier = Modifier.size((12 * scaleFactor).dp)
                    ) {
                        drawCircle(
                            color = primaryColor,
                            radius = size.minDimension / 2
                        )
                    }
                }

                // Value display
                if (isLoading) {
                    Text(
                        text = "No data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = (12 * scaleFactor).sp
                    )
                } else if (isError) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = (12 * scaleFactor).sp
                    )
                } else {
                    Text(
                        text = "${currentValue.toInt()}",
                        fontSize = (18 * scaleFactor).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = (12 * scaleFactor).sp
                    )
                }
            }
        }
    }
}

/**
 * Draws the analog gauge with arc background, value arc, and needle.
 */
private fun DrawScope.drawGauge(
    percentage: Float,
    primaryColor: Color,
    backgroundColor: Color,
    isError: Boolean,
    errorColor: Color,
    scaleFactor: Float
) {
    val center = size.center
    val radius = size.minDimension / 2 - 20.dp.toPx() * scaleFactor
    val strokeWidth = 8.dp.toPx() * scaleFactor

    // Define the gauge arc angles (240 degrees total, starting from bottom left)
    val startAngle = 150f // Start angle (bottom left)
    val sweepAngle = 240f // Total sweep angle

    // Draw background arc
    drawArc(
        color = backgroundColor,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        topLeft = Offset(center.x - radius, center.y - radius),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Draw value arc
    val valueColor = if (isError) errorColor else primaryColor
    val valueSweep = sweepAngle * percentage

    if (valueSweep > 0) {
        drawArc(
            color = valueColor,
            startAngle = startAngle,
            sweepAngle = valueSweep,
            useCenter = false,
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            topLeft = Offset(center.x - radius, center.y - radius),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }

    // Draw needle
    if (!isError) {
        val needleAngle = Math.toRadians((startAngle + valueSweep).toDouble())
        val needleLength = radius - 10.dp.toPx() * scaleFactor
        val needleEnd = Offset(
            center.x + (needleLength * cos(needleAngle)).toFloat(),
            center.y + (needleLength * sin(needleAngle)).toFloat()
        )

        drawLine(
            color = valueColor,
            start = center,
            end = needleEnd,
            strokeWidth = 3.dp.toPx() * scaleFactor,
            cap = StrokeCap.Round
        )
    }

    // Draw tick marks
    drawTickMarks(
        center = center,
        radius = radius,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        tickColor = backgroundColor.copy(alpha = 0.8f),
        scaleFactor = scaleFactor
    )
}

/**
 * Draws tick marks around the gauge.
 */
private fun DrawScope.drawTickMarks(
    center: Offset,
    radius: Float,
    startAngle: Float,
    sweepAngle: Float,
    tickColor: Color,
    scaleFactor: Float,
    numberOfTicks: Int = 9
) {
    val tickLength = 8.dp.toPx() * scaleFactor
    val tickWidth = 2.dp.toPx() * scaleFactor

    for (i in 0..numberOfTicks) {
        val angle = Math.toRadians((startAngle + (sweepAngle * i / numberOfTicks)).toDouble())
        val tickStart = Offset(
            center.x + ((radius - tickLength) * cos(angle)).toFloat(),
            center.y + ((radius - tickLength) * sin(angle)).toFloat()
        )
        val tickEnd = Offset(
            center.x + (radius * cos(angle)).toFloat(),
            center.y + (radius * sin(angle)).toFloat()
        )

        drawLine(
            color = tickColor,
            start = tickStart,
            end = tickEnd,
            strokeWidth = tickWidth,
            cap = StrokeCap.Round
        )
    }
} 