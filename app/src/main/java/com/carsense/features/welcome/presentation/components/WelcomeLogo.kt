package com.carsense.features.welcome.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.carsense.R

/**
 * Displays the CarSense logo with appropriate color filtering based on the theme.
 * In light mode, the logo is tinted black, while in dark mode the original colors are preserved.
 */
@Composable
fun WelcomeLogo() {
    val isDarkTheme = isSystemInDarkTheme()

    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "CarSense Logo",
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .heightIn(max = 70.dp),
        colorFilter = if (!isDarkTheme) {
            // Apply black filter in light mode
            ColorFilter.tint(Color.Black)
        } else {
            null // No filter in dark mode
        }
    )
} 