package io.issac.pleosandroid.feature.badges.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.domain.collection.model.CardTemplate
import io.issac.pleosandroid.core.domain.collection.model.CollectedCard
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.time.ZoneId
import javax.inject.Inject

internal class CardUiMapper @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun card(value: CollectedCard): CollectionCard {
        val place = value.place?.let(::place)
        val front = value.front
        return CollectionCard(
            CollectionCardDesign(
                value.id, front?.title ?: place?.displayName ?: "운전습관 카드", front?.subtitle.orEmpty(),
                front?.context.orEmpty(), front?.series.orEmpty(), place,
                asset(front?.artworkKey), asset(front?.glossKey), asset(front?.engravingKey),
                front?.template == CardTemplate.Postcard,
            ),
            value.earnedLevel,
            value.revealedLevel,
            value.memories.mapIndexed { index, memory ->
                CardMemory(
                    memory.id,
                    memory.localDate,
                    if (index == 0) {
                        "처음 간직한 날"
                    } else if (place != null) {
                        "다시 만난 ${place.displayName}"
                    } else {
                        "차분히 마친 여정"
                    },
                    if (place != null) "${place.displayName} · 방문 기록" else "운전습관 · 주행 기록",
                    journeyId = memory.journeyId,
                )
            },
            value.acquiredAt.atZone(ZoneId.systemDefault()).toLocalDate(),
        )
    }

    fun place(value: Place): CollectionPlace {
        // These six positions belong to the existing decorative map, not the location/qualification model.
        val marker = when (value.id) {
            "haeundae" -> .77f to .43f
            "gwangalli" -> .51f to .57f
            "cheongsapo" -> .88f to .65f
            "huinnyeoul" -> .25f to .77f
            "igidae" -> .44f to .80f
            "songdo" -> .10f to .67f
            else -> null
        }
        return CollectionPlace(
            value.id,
            value.name,
            value.district,
            value.category,
            value.latitude,
            value.longitude,
            marker?.first,
            marker?.second,
        )
    }

    private fun asset(key: String?): Int =
        key?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
}
