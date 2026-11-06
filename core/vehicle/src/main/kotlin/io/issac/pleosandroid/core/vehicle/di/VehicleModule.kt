package io.issac.pleosandroid.core.vehicle.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.domain.collection.repository.VisitLocationSource
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import io.issac.pleosandroid.core.vehicle.datasource.AndroidVisitLocationSource
import io.issac.pleosandroid.core.vehicle.datasource.CarGearMonitor
import io.issac.pleosandroid.core.vehicle.datasource.CarSpeedMonitor
import io.issac.pleosandroid.core.vehicle.datasource.CarUxDataSource
import io.issac.pleosandroid.core.vehicle.datasource.CarUxMonitor
import io.issac.pleosandroid.core.vehicle.datasource.VehicleGearDataSource
import io.issac.pleosandroid.core.vehicle.datasource.VehicleMotionDataSource
import io.issac.pleosandroid.core.vehicle.repository.impl.DefaultVehicleRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VehicleModule {
    @Binds
    abstract fun bindVisitLocationSource(source: AndroidVisitLocationSource): VisitLocationSource

    @Binds
    abstract fun bindVehicleRepository(repository: DefaultVehicleRepository): VehicleRepository

    @Binds
    abstract fun bindMotionDataSource(source: CarSpeedMonitor): VehicleMotionDataSource

    @Binds
    abstract fun bindUxDataSource(source: CarUxMonitor): CarUxDataSource

    @Binds
    abstract fun bindGearDataSource(source: CarGearMonitor): VehicleGearDataSource
}
