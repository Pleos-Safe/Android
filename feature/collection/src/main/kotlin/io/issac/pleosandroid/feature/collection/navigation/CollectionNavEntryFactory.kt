package io.issac.pleosandroid.feature.collection.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.CardDetailRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MotionGalleryRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.OpenPackRoute
import io.issac.pleosandroid.core.navigation.PackMotionRoute
import io.issac.pleosandroid.core.navigation.PackOddsRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.feature.collection.CollectionDestination
import io.issac.pleosandroid.feature.collection.gallery.MotionGallery
import io.issac.pleosandroid.feature.collection.gallery.PackMotionPlayer
import io.issac.pleosandroid.feature.collection.pack.PackOdds
import javax.inject.Inject

internal class CollectionNavEntryFactory @Inject constructor() : NavEntryFactory {
    override fun create(route: AppRoute): NavEntryResult? = when (route) {
        BadgesRoute, PackShelfRoute, is CardDetailRoute, is OpenPackRoute ->
            NavEntryResult { padding -> CollectionDestination(route, Modifier.padding(padding)) }

        PackOddsRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            PackOdds({ router.pop() }, Modifier.padding(padding))
        }

        is MotionGalleryRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            MotionGallery(route, { router.navigate(it) }, { router.pop() }, Modifier.padding(padding))
        }

        is PackMotionRoute -> NavEntryResult { padding ->
            val router = LocalNavigationRouter.current
            PackMotionPlayer(route, { router.pop() }, Modifier.padding(padding))
        }

        else -> null
    }
}
