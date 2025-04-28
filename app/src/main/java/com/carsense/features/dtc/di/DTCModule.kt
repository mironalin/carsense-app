package com.carsense.features.dtc.di

import com.carsense.features.dtc.data.repository.DTCRepositoryImpl
import com.carsense.features.dtc.domain.repository.DTCRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DTCModule {

    @Binds
    @Singleton
    abstract fun bindDTCRepository(dtcRepositoryImpl: DTCRepositoryImpl): DTCRepository
}
