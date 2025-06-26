package com.carsense.features.welcome.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDashed
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top app bar for the Welcome screen that shows user info, connection status,
 * and login/logout buttons.
 *
 * @param isLoggedIn Whether the user is currently logged in
 * @param userName The name of the logged-in user, or null if not logged in
 * @param isConnected Whether the device is connected to an OBD2 adapter
 * @param deviceName The name of the connected device, or null if not connected
 * @param isLogoutLoading Whether a logout operation is in progress
 * @param onLoginLogoutClick Callback when the login/logout button is clicked
 * @param onSettingsClick Callback when the settings button is clicked
 * @param showSnackbar Function to show a snackbar message
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeTopBar(
    isLoggedIn: Boolean,
    userName: String?,
    isConnected: Boolean,
    deviceName: String?,
    isLogoutLoading: Boolean,
    onLoginLogoutClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showSnackbar: suspend (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    // Create a local loading state that will be set to false after logout
    var internalLogoutLoading by remember { mutableStateOf(isLogoutLoading) }

    // Update the internal state when isLogoutLoading changes
    LaunchedEffect(isLogoutLoading) {
        internalLogoutLoading = isLogoutLoading
    }

    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoggedIn) {
                    Text(
                        text = "Welcome, ${userName ?: "User"}!",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Lucide.CircleCheck else Lucide.CircleDashed,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isConnected) "Connected to ${deviceName ?: "OBD2"}" else "Not connected",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "CarSense",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Your Smart Driving Companion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        if (isLoggedIn && isConnected) {
                            // Prevent logout when connected
                            scope.launch {
                                showSnackbar("Please disconnect from OBD2 device before logging out")
                            }
                        } else if (isLoggedIn && !internalLogoutLoading && !isConnected) {
                            internalLogoutLoading = true
                            scope.launch {
                                // Simulate network delay for logout only
                                delay(1000)
                                onLoginLogoutClick()
                                // Reset the loading state after logout
                                internalLogoutLoading = false
                            }
                        } else if (!isLoggedIn) {
                            // Immediately proceed to login screen without loading
                            onLoginLogoutClick()
                        }
                    },
                    enabled = !internalLogoutLoading
                ) {
                    if (isLoggedIn && internalLogoutLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isLoggedIn) Lucide.LogOut else Lucide.LogIn,
                            contentDescription = if (isLoggedIn) "Logout" else "Login/Register",
                            tint = if (isLoggedIn && isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.5f
                            ) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { onSettingsClick() }) {
                Icon(
                    imageVector = Lucide.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}