package com.carsense.features.diagnostics.di

import com.carsense.features.diagnostics.data.api.DiagnosticsApiService
import com.carsense.features.diagnostics.data.repository.DiagnosticRepositoryImpl
import com.carsense.features.diagnostics.domain.repository.DiagnosticRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {

    @Binds
    @Singleton
    abstract fun bindDiagnosticRepository(
        diagnosticRepositoryImpl: DiagnosticRepositoryImpl
    ): DiagnosticRepository

    companion object {
        @Provides
        @Singleton
        fun provideDiagnosticsApiService(retrofit: Retrofit): DiagnosticsApiService {
            return retrofit.create(DiagnosticsApiService::class.java)
        }
    }
} 