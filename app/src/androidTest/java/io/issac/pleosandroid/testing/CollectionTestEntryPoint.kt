package io.issac.pleosandroid.testing

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.domain.cards.CardAlbumRepository
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.repository.VehicleRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CollectionTestEntryPoint {
    fun album(): CardAlbumRepository
    fun progress(): ProgressRepository
    fun history(): HistoryRepository
    fun recorder(): DrivingRecorder
    fun vehicle(): VehicleRepository
}
