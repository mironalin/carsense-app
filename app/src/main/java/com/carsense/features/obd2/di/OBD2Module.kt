package com.carsense.features.obd2.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class OBD2Module {
    // OBD2-specific bindings would go here
}
