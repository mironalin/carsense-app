package com.carsense.core.di

import android.content.Context
import com.carsense.BuildConfig
import com.carsense.core.auth.TokenStorageService
import com.carsense.core.network.AuthApiService
import com.carsense.features.vehicles.data.api.VehicleApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.io.IOException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val API_BASE_URL =
        "https://carsense.alinmiron.live/api/" // Ensure trailing slash

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStorageService: TokenStorageService): AuthInterceptor {
        return AuthInterceptor(tokenStorageService)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context, authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Timber.tag("OkHttp").d(message)
        }.apply {
            level =
                if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder().addInterceptor(authInterceptor) // Adds auth token to requests
            .addInterceptor(loggingInterceptor) // Logs network requests and responses
            // Add other configurations like timeouts if needed
            // .connectTimeout(30, TimeUnit.SECONDS)
            // .readTimeout(30, TimeUnit.SECONDS)
            // .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory()) // For Kotlin support (reflection-based)
            // Add any custom adapters if needed here
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder().baseUrl(API_BASE_URL).client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi)).build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideVehicleApiService(retrofit: Retrofit): VehicleApiService {
        return retrofit.create(VehicleApiService::class.java)
    }
}

/**
 * Interceptor to add the Authorization token to requests.
 */
class AuthInterceptor(private val tokenStorageService: TokenStorageService) : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenStorageService.getAuthToken()

        val requestBuilder = originalRequest.newBuilder()
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
            Timber.tag("AuthInterceptor")
                .d("Authorization token added to request for ${originalRequest.url}")
        } else {
            Timber.tag("AuthInterceptor")
                .d("No auth token found, request to ${originalRequest.url} proceeds without token.")
        }

        // Add other common headers if needed
        // requestBuilder.header("Accept", "application/json")
        // requestBuilder.header("Content-Type", "application/json") // Usually handled by Retrofit/Moshi for POST/PUT

        return chain.proceed(requestBuilder.build())
    }
} 