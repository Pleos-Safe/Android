package io.issac.pleosandroid.feature.home.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.home.navigation.HomeNavEntryFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HomeNavigationModule {
    @Binds
    @IntoSet
    abstract fun entry(factory: HomeNavEntryFactory): NavEntryFactory
}
