package io.issac.pleosandroid.core.vehicle.place

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.domain.place.JourneyArrivalSource
import io.issac.pleosandroid.core.domain.place.PlaceSessionRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaceSessionModule {
    @Binds
    abstract fun bindPlaceSession(repository: DefaultPlaceSessionRepository): PlaceSessionRepository

    @Binds
    abstract fun bindJourneyArrival(source: DefaultPlaceSessionRepository): JourneyArrivalSource
}
