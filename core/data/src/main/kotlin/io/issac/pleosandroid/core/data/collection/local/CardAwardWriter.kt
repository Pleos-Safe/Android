package io.issac.pleosandroid.core.data.collection.local

import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.CardLevelStateEntity
import io.issac.pleosandroid.core.database.entity.CardMemoryEntity
import io.issac.pleosandroid.core.database.entity.OwnedCardEntity
import io.issac.pleosandroid.core.database.entity.RewardNoticeEntity
import io.issac.pleosandroid.core.domain.collection.model.CardChange
import io.issac.pleosandroid.core.domain.collection.model.CardKind
import io.issac.pleosandroid.core.domain.collection.model.CardProgress
import io.issac.pleosandroid.core.domain.collection.model.ProgressReceipt
import io.issac.pleosandroid.core.domain.collection.policy.AchievementEvaluator
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Runs only inside the source write transaction, using a pure domain evaluator. */
internal class CardAwardWriter @Inject constructor() {
    private val evaluator = AchievementEvaluator()

    suspend fun reconcile(
        dao: CollectionDao,
        operationId: String,
        allowReduction: Boolean = false,
        allowGrowth: Boolean = true,
        cardIds: Set<String>? = null,
    ): ProgressReceipt {
        val state = dao.snapshot()
        val changes = mutableListOf<CardChange>()
        val progress = mutableListOf<CardProgress>()
        val notices = mutableListOf<String>()
        val now = Instant.now().toEpochMilli()
        for (card in state.definitions.filter { cardIds == null || it.id in cardIds }) {
            val old = state.owned.find { it.cardId == card.id }
            if (!card.active && !allowReduction) continue
            val evaluated = state.evaluate(card, allowReduction, allowGrowth)
            val earned = evaluated.earnedLevel
            progress.add(evaluated)
            dao.deleteMemories(card.id)
            val applicableRules = state.rules.filter {
                it.cardId == card.id && (
                    it.version == card.ruleVersion ||
                        state.levels.any { grant ->
                            grant.ownershipId == old?.ownershipId && grant.ruleVersion == it.version
                        }
                    )
            }
            val memories = when (card.kind) {
                CardKind.Place -> state.visitsForCardAwards.filter { visit ->
                    visit.placeId == card.placeId &&
                        applicableRules.any { !visit.confirmedAt.isBefore(it.eligibleFrom) }
                }.map { visit ->
                    CardMemoryEntity(
                        "${card.id}:visit:${visit.id}",
                        card.id,
                        visit.id,
                        null,
                        visit.confirmedAt.toEpochMilli(),
                        visit.confirmedAt.atZone(ZoneId.of(visit.zoneId)).toLocalDate().toString(),
                    )
                }

                CardKind.Safety -> state.journeys.filter { journey ->
                    applicableRules.any { evaluator.qualifies(it, journey) }
                }.map { journey ->
                    CardMemoryEntity(
                        "${card.id}:journey:${journey.id}",
                        card.id,
                        null,
                        journey.id,
                        journey.endedAt.toEpochMilli(),
                        journey.endedAt.atZone(ZoneId.of(journey.zoneId)).toLocalDate().toString(),
                    )
                }
            }
            dao.putMemories(memories)
            if (earned == 0) {
                if (old != null) {
                    dao.deleteOwned(card.id)
                    changes.add(CardChange(card.id, old.earnedLevel, 0))
                }
                continue
            }
            val owner = old?.ownershipId ?: UUID.randomUUID().toString()
            val revealed = old?.revealedLevel?.coerceAtMost(earned)
            val acquiredAt = old?.acquiredAt ?: now
            dao.putOwned(OwnedCardEntity(card.id, owner, earned, revealed, acquiredAt, (old?.revision ?: 0) + 1))
            dao.removeHigherLevels(owner, earned)
            dao.removeInvalidNotices(owner, earned)
            val oldLevels = state.levels.filter { it.ownershipId == owner }.associateBy { it.level }
            dao.putLevels(
                (1..earned).map { level ->
                    oldLevels[level]
                        ?: CardLevelStateEntity(owner, level, UUID.randomUUID().toString(), now, card.ruleVersion, null)
                },
            )
            val from = old?.earnedLevel ?: 0
            if (from != earned) {
                changes.add(CardChange(card.id, from, earned))
                if (earned > from) {
                    val noticeId = UUID.randomUUID().toString()
                    dao.insertNotice(RewardNoticeEntity(noticeId, operationId, card.id, owner, from, earned, now, null))
                    notices.add(noticeId)
                }
            }
        }
        dao.touch()
        return ProgressReceipt(operationId, changes, progress, notices)
    }
}
