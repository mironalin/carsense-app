package com.carsense.core.auth

// import dagger.hilt.android.scopes.ActivityScoped // Or Singleton if using ApplicationContext - REMOVED
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

// If you plan to use this from ViewModels or other places that don't have direct Activity context,
// consider making it a Singleton and injecting ApplicationContext.
// For now, ActivityScoped with ActivityContext is fine if primarily launched from an Activity.
// @ActivityScoped // REMOVED for diagnostics
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context, // Use ActivityContext for launching CCT
    private val tokenStorageService: TokenStorageService
) {

    companion object {
        private const val AUTH_BASE_URL = "https://api.carsense.workers.dev/api/android-auth"
        private const val REDIRECT_URI = "carsense://auth-callback"
    }

    /**
     * Checks if the user is logged in by verifying if a token exists
     */
    fun isLoggedIn(): Boolean {
        return tokenStorageService.getAuthToken() != null
    }

    /**
     * Logs the user out by clearing the auth token
     */
    fun logout() {
        tokenStorageService.clearAuthToken()
        Timber.d("User logged out - auth token cleared")
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