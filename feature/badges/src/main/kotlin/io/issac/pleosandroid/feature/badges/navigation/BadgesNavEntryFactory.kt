package io.issac.pleosandroid.feature.badges.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.CardDetailRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.SafetyCollectionRoute
import javax.inject.Inject

internal class BadgesNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        BadgesRoute, SafetyCollectionRoute, UnopenedCardsRoute,
        is CardDetailRoute, is CardMemoriesRoute, is RevealCardRoute,
        is RepresentativeCardRoute, is CollectionGuideRoute, is CollectedPlaceRoute, is CollectionMemoryRoute,
        ->
            NavEntryResult { padding -> BadgesRouteContent(route, Modifier.padding(padding)) }

        else -> null
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BadgesNavigationModule {
    @Binds
    @IntoSet
    abstract fun bindBadgesNavEntryFactory(factory: BadgesNavEntryFactory): NavEntryFactory
}
