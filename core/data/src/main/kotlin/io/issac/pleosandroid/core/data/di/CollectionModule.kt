package io.issac.pleosandroid.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.data.collection.local.CatalogLoader
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.repository.LocalBadgeRegionSource
import io.issac.pleosandroid.core.data.collection.repository.LocalCatalogRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalCollectionRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalDemoRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalHistoryRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalPlaceRepository
import io.issac.pleosandroid.core.data.collection.repository.LocalProgressRepository
import io.issac.pleosandroid.core.database.CollectionDatabaseFactory
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.domain.collection.repository.CatalogRepository
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.collection.usecase.BeginCardRevealUseCase
import io.issac.pleosandroid.core.domain.collection.usecase.CompleteCardRevealUseCase
import io.issac.pleosandroid.core.domain.collection.usecase.SetRepresentativeCardUseCase
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CollectionModule {
    @Binds @Singleton
    abstract fun source(impl: CatalogLoader): CatalogSource

    @Binds @Singleton
    abstract fun database(impl: CollectionDatabaseFactory): CollectionDatabaseProvider

    @Binds @Singleton
    abstract fun collection(impl: LocalCollectionRepository): CollectionRepository

    @Binds @Singleton
    abstract fun progress(impl: LocalProgressRepository): ProgressRepository

    @Binds @Singleton
    abstract fun places(impl: LocalPlaceRepository): PlaceRepository

    @Binds @Singleton
    abstract fun history(impl: LocalHistoryRepository): HistoryRepository

    @Binds @Singleton
    abstract fun catalog(impl: LocalCatalogRepository): CatalogRepository

    @Binds @Singleton
    abstract fun demo(impl: LocalDemoRepository): CollectionDemoRepository

    @Binds @Singleton
    abstract fun regions(impl: LocalBadgeRegionSource): BadgeRegionSource

    companion object {
        @Provides fun beginReveal(cards: CollectionRepository, vehicle: VehicleRepository) =
            BeginCardRevealUseCase(cards, vehicle)

        @Provides fun completeReveal(cards: CollectionRepository, vehicle: VehicleRepository) =
            CompleteCardRevealUseCase(cards, vehicle)

        @Provides fun representative(cards: CollectionRepository, vehicle: VehicleRepository) =
            SetRepresentativeCardUseCase(cards, vehicle)
    }
}
