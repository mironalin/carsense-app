package com.carsense.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Module for binding repository implementations to their interfaces.
 * Vehicle repositories have been moved to the vehicles feature module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    // Vehicle repository binding has been moved to com.carsense.features.vehicles.di.VehicleModule
} 