package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.data.collection.mapper.toDomain
import io.issac.pleosandroid.core.data.collection.mapper.toEntity
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.CatalogStateEntity
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.CatalogResult
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.security.MessageDigest

internal fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

/** Validation precedes writes; callers enclose this in their transaction. */
internal suspend fun applyCatalog(
    dao: CollectionDao,
    pack: CatalogPack,
    hasAsset: (String) -> Boolean,
): CatalogResult {
    val hash = fingerprint(pack.toString())
    val current = dao.catalogState()
    if (current?.version == pack.version && current.hash == hash) return CatalogResult.Unchanged
    if (pack.version <= (current?.version ?: 0)) return CatalogResult.Rejected("Content version must increase")
    if (pack.places.distinctBy { it.id }.size != pack.places.size ||
        pack.cards.distinctBy { it.id }.size != pack.cards.size ||
        pack.rules.distinctBy { Triple(it.cardId, it.level, it.version) }.size != pack.rules.size
    ) {
        return CatalogResult.Rejected("Duplicate content identifiers")
    }
    val places = (dao.places().map { it.toDomain() } + pack.places).associateBy { it.id }
    val existingCards = dao.definitions().map { it.toDomain() }.associateBy { it.id }
    val cards = existingCards + pack.cards.associateBy { it.id }
    val oldRules = dao.rules().map { it.toDomain() }
    if (pack.places.any {
            it.id.isBlank() || it.name.isBlank() || !it.latitude.isFinite() || !it.longitude.isFinite() ||
                it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 ||
                !it.radiusMeters.isFinite() || it.radiusMeters <= 0 || it.minimumDwellSeconds < 0 ||
                !hasAsset(it.imageKey)
        }
    ) {
        return CatalogResult.Rejected("Invalid place or missing image")
    }
    if (pack.cards.any { card ->
            val old = existingCards[card.id]
            card.id.isBlank() || card.ruleVersion < 1 ||
                (card.kind == CardKind.Place && card.placeId !in places) ||
                (card.kind == CardKind.Safety && card.placeId != null) ||
                (
                    old != null && (
                        old.kind != card.kind || old.placeId != card.placeId || old.ruleVersion > card.ruleVersion
                        )
                    ) ||
                listOf(card.front.artworkKey, card.front.glossKey, card.front.engravingKey).any { !hasAsset(it) }
        }
    ) {
        return CatalogResult.Rejected("Invalid card identity or missing artwork")
    }
    if (pack.rules.any { rule ->
            val hasJourneyConditions = rule.minimumScore != null ||
                rule.minimumDistanceMeters != 0L || rule.maximumHarshStarts != null
            rule.cardId !in cards || rule.level !in 1..5 || rule.version < 1 || rule.requiredCount < 1 ||
                rule.minimumDistanceMeters < 0 || (rule.minimumScore != null && rule.minimumScore !in 0..100) ||
                (rule.maximumHarshStarts?.let { it < 0 } == true) ||
                (cards[rule.cardId]?.kind == CardKind.Place && hasJourneyConditions) ||
                oldRules.any {
                    it.cardId == rule.cardId && it.level == rule.level && it.version == rule.version && it != rule
                }
        }
    ) {
        return CatalogResult.Rejected("Invalid rule or mutation of an existing rule version")
    }
    val mergedRules = (oldRules + pack.rules).distinct()
    for (card in cards.values) {
        val levels = mergedRules.filter { it.cardId == card.id && it.version == card.ruleVersion }.sortedBy { it.level }
        if (levels.map { it.level } != (1..5).toList() || levels.zipWithNext().any { (a, b) ->
                b.requiredCount < a.requiredCount || b.eligibleFrom != a.eligibleFrom ||
                    (b.minimumScore ?: 0) < (a.minimumScore ?: 0) ||
                    b.minimumDistanceMeters < a.minimumDistanceMeters ||
                    (b.maximumHarshStarts ?: Int.MAX_VALUE) > (a.maximumHarshStarts ?: Int.MAX_VALUE)
            }
        ) {
            return CatalogResult.Rejected("Each card needs five cumulative level rules")
        }
    }
    dao.putPlaces(pack.places.map { it.toEntity() })
    dao.putDefinitions(pack.cards.map { it.toEntity() })
    dao.insertRules(pack.rules.filter { it !in oldRules }.map { it.toEntity() })
    dao.putCatalogState(CatalogStateEntity(version = pack.version, hash = hash))
    dao.touch()
    return CatalogResult.Applied
}
