package com.carsense.features.welcome.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.R
import com.carsense.features.welcome.presentation.viewmodel.WelcomeEvent
import com.carsense.features.welcome.presentation.viewmodel.WelcomeViewModel
import com.carsense.ui.theme.CarSenseTheme
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon

@Composable
fun WelcomeScreen(
    onConnectClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDarkModeToggle: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    BackHandler(enabled = true) { }

    Box(
        modifier = Modifier.fillMaxSize()
        //            .background(Color.Black)
    ) {
        // Center content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo and Brand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "CarSense Logo",
                    colorFilter =
                        if (!isSystemInDarkTheme()) ColorFilter.tint(Color.Black) else null
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Connect Button
            Button(
                onClick = {
                    viewModel.onEvent(WelcomeEvent.Connect)
                    onConnectClick()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Connect",
                )
                Text(
                    text = "Connect",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // Bottom navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    viewModel.onEvent(WelcomeEvent.ToggleDarkMode)
                    onDarkModeToggle()
                }
            ) {
                Icon(
                    imageVector = Lucide.Moon,
                    contentDescription = "Dark Mode",
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = {
                    viewModel.onEvent(WelcomeEvent.OpenSettings)
                    onSettingsClick()
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun WelcomeScreenPreview() {
    CarSenseTheme {
        Surface {
            CarSenseTheme {
                WelcomeScreen(onConnectClick = {}, onSettingsClick = {}, onDarkModeToggle = {})
            }
        }
    }
}
