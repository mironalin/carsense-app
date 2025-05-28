package com.carsense.features.welcome.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Footer component that displays a disconnect button.
 * Typically used at the bottom of the screen when connected to an OBD2 device.
 *
 * @param deviceName Name of the connected device, or null if not available
 * @param onDisconnectClick Callback when the disconnect button is clicked
 */
@Composable
fun DisconnectFooter(
    deviceName: String?,
    onDisconnectClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { onDisconnectClick() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            )
        ) {
            Text(
                text = "Disconnect from ${deviceName ?: "OBD2"}",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}