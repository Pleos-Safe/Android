package io.issac.pleosandroid.feature.collection.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.collection.navigation.CollectionNavEntryFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CollectionNavigationModule {
    @Binds
    @IntoSet
    abstract fun entry(factory: CollectionNavEntryFactory): NavEntryFactory
}
