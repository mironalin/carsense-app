package com.carsense.core.auth

// import dagger.hilt.android.scopes.ActivityScoped // Or Singleton if using ApplicationContext - REMOVED
import android.content.Context
import android.net.Uri
import com.carsense.core.network.AuthApiService // Import the service
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
// HttpURLConnection and related imports are no longer needed here for logout
// import java.net.HttpURLConnection
// import java.net.URL
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.withContext
// import org.json.JSONObject

// If you plan to use this from ViewModels or other places that don't have direct Activity context,
// consider making it a Singleton and injecting ApplicationContext.
// For now, ActivityScoped with ActivityContext is fine if primarily launched from an Activity.
// @ActivityScoped // REMOVED for diagnostics
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context, // Use ActivityContext for launching CCT
    private val tokenStorageService: TokenStorageService,
    private val authApiService: AuthApiService // Injected AuthApiService
) {

    companion object {
        private const val AUTH_BASE_URL = "https://api.carsense.workers.dev/api/android-auth"
        // API_BASE_URL is now configured in NetworkModule for Retrofit
        private const val REDIRECT_URI = "carsense://auth-callback"
    }

    /**
     * Checks if the user is logged in by verifying if a token exists
     */
    fun isLoggedIn(): Boolean {
        return tokenStorageService.getAuthToken() != null
    }

    /**
     * Clears local tokens and logs out the user from the device.
     */
    fun logoutFromDeviceOnly() {
        tokenStorageService.clearAuthToken()
        tokenStorageService.clearCsrfState() // Also clear CSRF state on logout
        Timber.d("User logged out from device - auth token and CSRF state cleared")
    }

    /**
     * Logs the user out by calling the backend sign-out endpoint and then clearing local tokens.
     * @return true if backend logout was successful (or if no token was present), false otherwise.
     */
    suspend fun logout(): Boolean {
        val token = tokenStorageService.getAuthToken()
        if (token == null) {
            Timber.d("No auth token found, already effectively logged out.")
            logoutFromDeviceOnly() // Ensure local state is also clean
            return true // No server call needed
        }

        // No need for withContext(Dispatchers.IO) here, Retrofit handles its own threading for suspend functions.
        return try {
            // AuthInterceptor will add the "Bearer $token" header.
            // The actual token value isn't passed directly here anymore.
            val response = authApiService.signOut() // Call the Retrofit service method

            if (response.isSuccessful) {
                Timber.i("Successfully logged out from backend using Retrofit. Code: ${response.code()}")
                logoutFromDeviceOnly()
                true
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                Timber.e("Failed to log out from backend using Retrofit. Code: ${response.code()}, Message: ${response.message()}, ErrorBody: $errorBody")
                logoutFromDeviceOnly() // Still log out locally for UX consistency
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during backend logout API call using Retrofit.")
            logoutFromDeviceOnly() // Still log out locally
            false
        }
    }

    fun generateSecureRandomState(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Prepares for authentication by generating and saving CSRF state
     * Returns the URL to use for authentication
     */
    fun prepareAuthFlow(): Uri {
        val state = generateSecureRandomState()
        tokenStorageService.saveCsrfState(state)
        Timber.d("Generated and saved CSRF state: $state")

        return Uri.parse(AUTH_BASE_URL)
            .buildUpon()
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .build()
    }

    /**
     * Get the auth URL without side effects (for testing or previews)
     */
    fun getAuthUrl(state: String = "test_state"): Uri {
        return Uri.parse(AUTH_BASE_URL)
            .buildUpon()
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .build()
    }
} 