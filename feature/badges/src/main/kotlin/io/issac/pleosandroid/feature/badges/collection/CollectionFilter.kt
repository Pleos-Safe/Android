package io.issac.pleosandroid.feature.badges.collection

import androidx.compose.runtime.saveable.listSaver
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import java.io.Serializable

/** Save just the last sort key and ID so removal can fall forward to the next surviving card. */
internal data class ShelfAnchor(val sortKey: String, val cardId: String) : Serializable

internal enum class CardOrder(val label: String) {
    FirstKept("처음 간직한 순"),
    RecentMemory("최근 기억 순"),
    Name("이름순"),
    ;

    fun anchor(card: CollectionCard): ShelfAnchor {
        val key = when (this) {
            FirstKept -> card.firstDate.toString()
            RecentMemory -> card.lastDate.toString()
            Name -> card.publicTitle
        }
        return ShelfAnchor(key, card.id)
    }

    fun compare(left: ShelfAnchor, right: ShelfAnchor): Int {
        val byKey = if (this == RecentMemory) {
            right.sortKey.compareTo(left.sortKey)
        } else {
            left.sortKey.compareTo(right.sortKey)
        }
        return if (byKey == 0) left.cardId.compareTo(right.cardId) else byKey
    }

    fun position(cards: List<CollectionCard>, saved: ShelfAnchor?): Int {
        if (saved == null || cards.isEmpty()) return 0
        val exact = cards.indexOfFirst { it.id == saved.cardId }
        if (exact >= 0) return exact
        val following = cards.indexOfFirst { compare(anchor(it), saved) > 0 }
        return if (following >= 0) following else cards.lastIndex
    }
}

internal data class CollectionFilter(
    val kind: CardKind? = null,
    val district: String? = null,
    val placeType: String? = null,
    val order: CardOrder = CardOrder.FirstKept,
) {
    val placeConditions get() = if (kind == CardKind.Safety) emptyList() else listOfNotNull(district, placeType)

    fun select(cards: List<CollectionCard>): List<CollectionCard> = cards.filter { card ->
        val inDistrict = district == null || card.place?.district == district
        val ofType = placeType == null || card.place?.typeLabel == placeType
        (kind == null || card.kind == kind) && (kind == CardKind.Safety || (inDistrict && ofType))
    }.sortedWith { a, b -> order.compare(order.anchor(a), order.anchor(b)) }

    fun clearedPlaces() = copy(district = null, placeType = null)

    fun showing(card: CollectionCard): CollectionFilter {
        if (card.kind == CardKind.Safety) return copy(kind = CardKind.Safety)
        return copy(
            kind = CardKind.Place,
            district = district.takeIf { it == card.place?.district },
            placeType = placeType.takeIf { it == card.place?.typeLabel },
        )
    }

    companion object {
        val Saver = listSaver<CollectionFilter, String>(
            save = { listOf(it.kind?.name.orEmpty(), it.district.orEmpty(), it.placeType.orEmpty(), it.order.name) },
            restore = {
                CollectionFilter(
                    CardKind.entries.find { kind -> kind.name == it[0] },
                    it[1].ifEmpty { null },
                    it[2].ifEmpty { null },
                    CardOrder.valueOf(it[3]),
                )
            },
        )
    }
}
