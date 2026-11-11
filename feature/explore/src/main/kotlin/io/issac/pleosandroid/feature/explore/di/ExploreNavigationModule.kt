package io.issac.pleosandroid.feature.explore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.explore.navigation.ExploreNavEntryFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ExploreNavigationModule {
    @Binds
    @IntoSet
    abstract fun bindExploreEntry(factory: ExploreNavEntryFactory): NavEntryFactory
}
