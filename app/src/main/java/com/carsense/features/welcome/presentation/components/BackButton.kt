package com.carsense.features.welcome.presentation.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

@Composable
fun BackButton(
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick, modifier = modifier
    ) {
        Icon(
            imageVector = Lucide.ArrowLeft, contentDescription = "Go back"
        )
    }
} 