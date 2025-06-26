package com.carsense.features.welcome.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.carsense.features.welcome.presentation.components.background.DecorativeElements
import com.carsense.features.welcome.presentation.components.background.FloatingCircles

/**
 * Background visual component for the welcome screen.
 * Creates floating geometric elements with subtle animations to make the screen more engaging.
 */
@Composable
fun WelcomeBackgroundVisuals(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "backgroundAnimation")

    // Rotation animations for different elements
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slowRotation"
    )

    val mediumRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mediumRotation"
    )

    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastRotation"
    )

    // Independent floating animations for each element
    val floatY1 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY1"
    )

    val floatY2 by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(5300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY2"
    )

    val floatY3 by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY3"
    )

    val floatY4 by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY4"
    )

    val floatX1 by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatX1"
    )

    val floatX2 by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(5800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatX2"
    )

    val floatX3 by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(6800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatX3"
    )

    // Pulsing alpha animation
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Floating circles component
        FloatingCircles(
            slowRotation = slowRotation,
            mediumRotation = mediumRotation,
            fastRotation = fastRotation,
            floatX1 = floatX1,
            floatX2 = floatX2,
            floatX3 = floatX3,
            floatY1 = floatY1,
            floatY2 = floatY2,
            floatY3 = floatY3,
            floatY4 = floatY4,
            pulseAlpha = pulseAlpha
        )

        // Decorative elements component
        DecorativeElements(
            slowRotation = slowRotation,
            fastRotation = fastRotation,
            floatX1 = floatX1,
            floatX2 = floatX2,
            floatX3 = floatX3,
            floatY1 = floatY1,
            floatY2 = floatY2,
            floatY3 = floatY3,
            floatY4 = floatY4
        )
    }
} 