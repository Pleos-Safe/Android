package io.issac.pleosandroid.feature.records.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.feature.records.navigation.RecordsNavEntryFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecordsNavigationModule {
    @Binds
    @IntoSet
    abstract fun bindRecordsEntry(factory: RecordsNavEntryFactory): NavEntryFactory
}
