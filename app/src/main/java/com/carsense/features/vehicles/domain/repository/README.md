# Vehicle Repository Interface

This directory will contain the repository interfaces for the vehicles feature.

## TODO: Migration from Core

The `VehicleRepository.kt` interface should be moved here from:
`com.carsense.core.repository.VehicleRepository`

When moving the interface:

1. Update the package declaration to `com.carsense.features.vehicles.domain.repository`
2. Update all imports across the codebase referencing this interface
3. Update the DI module in `com.carsense.features.vehicles.di.VehicleModule` to use the new path 