package com.carsense.features.welcome.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.carsense.R

/**
 * Displays the CarSense logo with appropriate color filtering based on the theme.
 * In light mode, the logo is tinted black, while in dark mode the original colors are preserved.
 * The logo is translucent to reduce its visual dominance.
 */
@Composable
fun WelcomeLogo(
    modifier: Modifier = Modifier.fillMaxWidth(0.85f), alpha: Float = 0.5f
) {
    val isDarkTheme = isSystemInDarkTheme()

    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "CarSense Logo",
        modifier = modifier
            .heightIn(max = 50.dp)
            .alpha(alpha),
        colorFilter = if (!isDarkTheme) {
            // Apply black filter in light mode with transparency
            ColorFilter.tint(Color.Black.copy(alpha = 0.8f))
        } else {
            null // No tint in dark mode, alpha modifier handles transparency
        }
    )
} 