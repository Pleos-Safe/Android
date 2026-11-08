package io.issac.pleosandroid.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.data.cards.DefaultCardAlbumRepository
import io.issac.pleosandroid.core.data.collection.local.CatalogLoader
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.repository.DefaultBadgeRegionSource
import io.issac.pleosandroid.core.data.collection.repository.DefaultCollectionDemoRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultHistoryRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultPlaceRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultProgressRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultVisitPackRepository
import io.issac.pleosandroid.core.data.membership.DefaultMembershipRepository
import io.issac.pleosandroid.core.data.points.DefaultPointRepository
import io.issac.pleosandroid.core.database.CollectionDatabaseFactory
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.domain.cards.CardAlbumRepository
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import io.issac.pleosandroid.core.domain.collection.repository.HistoryRepository
import io.issac.pleosandroid.core.domain.collection.repository.PlaceRepository
import io.issac.pleosandroid.core.domain.collection.repository.ProgressRepository
import io.issac.pleosandroid.core.domain.collection.repository.VisitPackRepository
import io.issac.pleosandroid.core.domain.membership.MembershipRepository
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CollectionModule {
    @Binds @Singleton
    abstract fun album(impl: DefaultCardAlbumRepository): CardAlbumRepository

    @Binds @Singleton
    abstract fun source(impl: CatalogLoader): CatalogSource

    @Binds @Singleton
    abstract fun database(impl: CollectionDatabaseFactory): CollectionDatabaseProvider

    @Binds @Singleton
    abstract fun progress(impl: DefaultProgressRepository): ProgressRepository

    @Binds @Singleton
    abstract fun membership(impl: DefaultMembershipRepository): MembershipRepository

    @Binds @Singleton
    abstract fun points(impl: DefaultPointRepository): PointRepository

    @Binds @Singleton
    abstract fun visitPacks(impl: DefaultVisitPackRepository): VisitPackRepository

    @Binds @Singleton
    abstract fun places(impl: DefaultPlaceRepository): PlaceRepository

    @Binds @Singleton
    abstract fun history(impl: DefaultHistoryRepository): HistoryRepository

    @Binds @Singleton
    abstract fun demo(impl: DefaultCollectionDemoRepository): CollectionDemoRepository

    @Binds @Singleton
    abstract fun regions(impl: DefaultBadgeRegionSource): BadgeRegionSource
}
