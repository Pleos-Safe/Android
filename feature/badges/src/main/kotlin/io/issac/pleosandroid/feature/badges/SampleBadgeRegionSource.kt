package io.issac.pleosandroid.feature.badges

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.issac.pleosandroid.core.domain.repository.BadgeRegionSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class SampleBadgeRegionSource @Inject constructor() : BadgeRegionSource {
    override val collectedRegionIds: Flow<Set<String>> = flowOf(
        SampleCollection.cards.filter { it.kind == CardKind.Place }.mapNotNull { it.regionId }.toSet(),
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BadgeRegionModule {
    @Binds
    abstract fun bindBadgeRegionSource(source: SampleBadgeRegionSource): BadgeRegionSource
}
