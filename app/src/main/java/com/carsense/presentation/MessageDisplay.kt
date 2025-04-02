package com.carsense.presentation

/** UI model for displaying messages in the OBD2 interface */
data class MessageDisplay(
    val content: String,
    val senderName: String,
    val isFromLocalUser: Boolean
)
