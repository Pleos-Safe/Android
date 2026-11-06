package io.issac.pleosandroid.debug

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.repository.VehicleRepository

/** Instrumented tests seed only the opt-in demo database through normal domain contracts. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CollectionDebugEntryPoint {
    fun demo(): CollectionDemoRepository
    fun progress(): ProgressRepository
    fun cards(): CollectionRepository
    fun history(): HistoryRepository
    fun recorder(): DrivingRecorder
    fun vehicle(): VehicleRepository
}
