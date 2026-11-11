package io.issac.pleosandroid.feature.forge.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.forge.navigation.ForgeNavEntryFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ForgeNavigationModule {
    @Binds
    @IntoSet
    abstract fun entry(factory: ForgeNavEntryFactory): NavEntryFactory
}
