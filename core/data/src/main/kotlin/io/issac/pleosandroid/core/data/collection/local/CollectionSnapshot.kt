package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.data.collection.mapper.toDomain
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.CardLevelStateEntity
import io.issac.pleosandroid.core.database.entity.OwnedCardEntity
import io.issac.pleosandroid.core.database.entity.RewardNoticeEntity
import io.issac.pleosandroid.core.domain.collection.model.CardDefinition
import io.issac.pleosandroid.core.domain.collection.model.CardLevelRule
import io.issac.pleosandroid.core.domain.collection.model.CardMemory
import io.issac.pleosandroid.core.domain.collection.model.CollectedCard
import io.issac.pleosandroid.core.domain.collection.model.CollectionSnapshot
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.time.Instant

internal data class StoredCollection(
    val generation: String,
    val places: List<Place>,
    val definitions: List<CardDefinition>,
    val rules: List<CardLevelRule>,
    val visits: List<ConfirmedVisit>,
    val journeys: List<JourneyRecord>,
    val owned: List<OwnedCardEntity>,
    val levels: List<CardLevelStateEntity>,
    val memories: List<CardMemory>,
    val memoryCards: Map<String, String>,
    val representativeId: String?,
    val notices: List<RewardNoticeEntity>,
) {
    fun card(id: String, allowFront: Boolean = false): CollectedCard? {
        val owned = owned.find { it.cardId == id } ?: return null
        val definition = definitions.first { it.id == id }
        return CollectedCard(
            id, owned.ownershipId, definition.kind, places.find { it.id == definition.placeId },
            definition.front.takeIf { allowFront || owned.revealedLevel != null },
            owned.earnedLevel, owned.revealedLevel, Instant.ofEpochMilli(owned.acquiredAt),
            memories.filter { memoryCards[it.id] == id },
        )
    }

    fun publicSnapshot() = CollectionSnapshot(owned.mapNotNull { card(it.cardId) }, representativeId, notices.size)
}

/** Caller holds a Room transaction, so all projections describe the same committed state. */
internal suspend fun CollectionDao.snapshot(): StoredCollection {
    val memories = memories()
    return StoredCollection(
        checkNotNull(userState()).generation, places().map { it.toDomain() }, definitions().map { it.toDomain() },
        rules().map { it.toDomain() }, visits().map { it.toDomain() }, journeys().map { it.toDomain() },
        ownedCards(), levels(), memories.map { it.toDomain() }, memories.associate { it.id to it.cardId },
        display()?.cardId, notices(),
    )
}
