package com.carsense.features.location.di

import android.content.Context
import com.carsense.features.location.data.api.LocationApiService
import com.carsense.features.location.data.repository.LocationRepositoryImpl
import com.carsense.features.location.data.service.AndroidLocationService
import com.carsense.features.location.data.service.LocationService
import com.carsense.features.location.domain.repository.LocationRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Dependency injection module for location-related components.
 */
@Module
@InstallIn(SingletonComponent::class)
class LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }

    @Provides
    @Singleton
    fun provideLocationApiService(retrofit: Retrofit): LocationApiService {
        return retrofit.create(LocationApiService::class.java)
    }

    /**
     * Module for binding service implementations
     */
    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds
        @Singleton
        fun bindLocationService(impl: AndroidLocationService): LocationService

        @Binds
        @Singleton
        fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository
    }
}
