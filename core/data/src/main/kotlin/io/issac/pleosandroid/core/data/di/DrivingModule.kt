package io.issac.pleosandroid.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.data.driving.DefaultDrivingRecorder
import io.issac.pleosandroid.core.data.driving.DefaultJourneyRepository
import io.issac.pleosandroid.core.data.driving.DefaultVisitRecorder
import io.issac.pleosandroid.core.data.driving.DeviceDrivingClock
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.domain.collection.repository.VisitRecorder
import io.issac.pleosandroid.core.domain.driving.repository.DrivingRecorder
import io.issac.pleosandroid.core.domain.driving.repository.JourneyRepository
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DrivingModule {
    @Binds abstract fun bindRecorder(impl: DefaultDrivingRecorder): DrivingRecorder

    @Binds abstract fun bindClock(impl: DeviceDrivingClock): DrivingClock

    @Binds abstract fun bindVisits(impl: DefaultVisitRecorder): VisitRecorder

    @Binds abstract fun bindJourney(impl: DefaultJourneyRepository): JourneyRepository

    companion object {
        @Provides @Singleton @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class ApplicationScope
