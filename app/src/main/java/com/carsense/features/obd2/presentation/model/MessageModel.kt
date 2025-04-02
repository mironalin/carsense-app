package com.carsense.features.obd2.presentation.model

/** Represents a message to be displayed in the UI */
data class MessageModel(
    val message: String,
    val isCommand: Boolean = false,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
