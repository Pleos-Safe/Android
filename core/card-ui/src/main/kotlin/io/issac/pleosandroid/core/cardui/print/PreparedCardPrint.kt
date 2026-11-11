package io.issac.pleosandroid.core.cardui.print

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.imageResource
import io.issac.pleosandroid.core.domain.cards.CardCopy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
class PreparedCardPrint internal constructor(internal val artwork: ImageBitmap, internal val plate: ImageBitmap)

/** Decode the drawn copy while its back is on screen. The screen retains only the current face. */
@Composable
fun rememberPreparedCardPrint(card: CardCopy?): PreparedCardPrint? {
    if (card == null) return null
    val resources = LocalResources.current
    val artwork = card.artworkResource
    val plate = card.plateResource
    return key(resources, LocalConfiguration.current, artwork, plate) {
        produceState<PreparedCardPrint?>(null) {
            value = withContext(Dispatchers.IO) {
                fun load(id: Int) = ImageBitmap.imageResource(resources, id).also { it.prepareToDraw() }
                PreparedCardPrint(load(artwork), load(plate))
            }
        }.value
    }
}
