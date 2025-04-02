package com.carsense.features.obd2.data

data class OBD2Response(
    val command: String,
    val rawData: String,
    val decodedValue: String,
    val unit: String,
    val isError: Boolean = false
)
