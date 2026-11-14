package io.issac.pleosandroid.feature.forge.di

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.NavEntryFactory
import io.issac.pleosandroid.core.navigation.NavEntryResult
import io.issac.pleosandroid.feature.forge.preview.ForgeMotionPlayer

@Module
@InstallIn(SingletonComponent::class)
internal object ForgePreviewNavigationModule {
    @Provides
    @IntoSet
    fun entry(): NavEntryFactory = NavEntryFactory { route ->
        if (route is ForgeMotionRoute) {
            NavEntryResult { padding ->
                val router = LocalNavigationRouter.current
                ForgeMotionPlayer(route, { router.pop() }, Modifier.padding(padding))
            }
        } else {
            null
        }
    }
}
