package com.carsense.features.vehicles.di

import com.carsense.features.vehicles.data.repository.VehicleRepositoryImpl
import com.carsense.features.vehicles.domain.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency Injection module for vehicle-related components.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {

    /**
     * Binds the VehicleRepositoryImpl implementation to the VehicleRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindVehicleRepository(vehicleRepositoryImpl: VehicleRepositoryImpl): VehicleRepository
} 