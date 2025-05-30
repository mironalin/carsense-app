package com.carsense.core.network

import com.squareup.moshi.Json

/**
 * Response model for the get-session endpoint.
 */
data class SessionResponse(
    val session: Session,
    val user: User
)

data class Session(
    val id: String,
    @Json(name = "expiresAt") val expiresAt: String,
    val token: String,
    @Json(name = "createdAt") val createdAt: String,
    @Json(name = "updatedAt") val updatedAt: String,
    @Json(name = "ipAddress") val ipAddress: String?,
    @Json(name = "userAgent") val userAgent: String?,
    @Json(name = "userId") val userId: String
)

data class User(
    val id: String,
    val name: String?,
    val email: String,
    @Json(name = "emailVerified") val emailVerified: Boolean,
    val image: String?,
    @Json(name = "createdAt") val createdAt: String,
    @Json(name = "updatedAt") val updatedAt: String,
    val role: String?
)