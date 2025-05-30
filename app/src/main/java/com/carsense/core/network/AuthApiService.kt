package com.carsense.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Data class for the logout response (if any specific structure)
// Based on the openapi, it seems to be: {"success": true}
// If the response body is truly just that or can be ignored, you might use Response<Unit>
// For now, let's assume a simple success flag might be useful.
data class LogoutResponse(val success: Boolean)

// Data class for an empty request body, useful for POST requests that send {}
// Using a Map or specific empty object can also work.
// For POST with empty JSON body {}, sending an empty Map or a specific empty object is common.
// Here, we'll make the @Body parameter in the interface optional with a default empty map.

/**
 * API Service interface for Authentication related endpoints.
 */
interface AuthApiService {

    /**
     * Signs out the current user.
     * The Authorization header with the bearer token will be added by the AuthInterceptor.
     */
    @POST("auth/sign-out") // Path relative to the base URL defined in Retrofit instance
    suspend fun signOut(
        @Body requestBody: Map<String, String> = emptyMap() // Retrofit will serialize this to {}
    ): Response<LogoutResponse> // Using Response<T> to get access to status code, headers etc.
    // If you only care about the deserialized body on success, use just LogoutResponse

    /**
     * Gets the current user's session information.
     * The Authorization header with the bearer token will be added by the AuthInterceptor.
     */
    @GET("auth/get-session")
    suspend fun getSession(): Response<SessionResponse>
} 