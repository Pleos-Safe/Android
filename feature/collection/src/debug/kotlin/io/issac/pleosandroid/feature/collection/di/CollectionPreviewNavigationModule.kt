package io.issac.pleosandroid.feature.collection.di

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.MotionGalleryRoute
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.core.navigation.PackMotionRoute
import io.issac.pleosandroid.feature.collection.gallery.MotionGallery
import io.issac.pleosandroid.feature.collection.gallery.PackMotionPlayer

@Module
@InstallIn(SingletonComponent::class)
internal object CollectionPreviewNavigationModule {
    @Provides
    @IntoSet
    fun entries(): NavEntryFactory = NavEntryFactory { route ->
        when (route) {
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
}
