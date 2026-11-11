package io.issac.pleosandroid.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf

class CollectionFocusResult internal constructor(private val target: MutableState<String?>) {
    val copyId: String? by target

    fun showCard(router: NavigationRouter, source: AppRoute, copyId: String) {
        if (!router.isActive(source)) return
        target.value = copyId
        router.replaceAll(BadgesRoute)
    }

    fun consumed(copyId: String) {
        if (target.value == copyId) target.value = null
    }
}

val LocalCollectionFocus = staticCompositionLocalOf<CollectionFocusResult> { error("Missing collection focus owner") }

@Composable
fun ProvideCollectionFocus(content: @Composable () -> Unit) {
    val target = rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(
        LocalCollectionFocus provides remember(target) {
            CollectionFocusResult(target)
        },
        content = content,
    )
}
