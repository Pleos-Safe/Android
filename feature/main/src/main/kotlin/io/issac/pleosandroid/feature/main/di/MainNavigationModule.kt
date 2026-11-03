package io.issac.pleosandroid.feature.main.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.main.MainNavEntryFactory

@Module
@InstallIn(SingletonComponent::class)
abstract class MainNavigationModule {
    @Binds
    @IntoSet
    abstract fun bindMainNavEntryFactory(factory: MainNavEntryFactory): NavEntryFactory
}
