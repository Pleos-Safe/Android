package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.local.snapshot
import io.issac.pleosandroid.core.database.entity.CollectionDisplayEntity
import io.issac.pleosandroid.core.domain.collection.model.BeginRevealResult
import io.issac.pleosandroid.core.domain.collection.model.MutationResult
import io.issac.pleosandroid.core.domain.collection.model.RevealSession
import io.issac.pleosandroid.core.domain.collection.model.RevealToken
import io.issac.pleosandroid.core.domain.collection.model.RewardNotice
import io.issac.pleosandroid.core.domain.collection.policy.AchievementEvaluator
import io.issac.pleosandroid.core.domain.collection.repository.CollectionRepository
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DefaultCollectionRepository @Inject constructor(private val store: CollectionStore) :
    CollectionRepository {
    override fun observeCollection() = store.observe().map { it.publicSnapshot() }.distinctUntilChanged()
    override fun observeCard(cardId: String) = store.observe().map { it.card(cardId) }.distinctUntilChanged()
    override fun observePendingReveals() = observeCollection().map { it.pendingCards }.distinctUntilChanged()
    override fun observeRepresentativeCard() = observeCollection().map { snapshot ->
        snapshot.cards.find { it.id == snapshot.representativeId && it.revealedLevel != null }
    }.distinctUntilChanged()

    override fun observeCardProgress(cardId: String) = store.observe().map { state ->
        state.definitions.find { it.id == cardId }?.let { definition ->
            val owned = state.owned.find { it.cardId == cardId }
            AchievementEvaluator().evaluate(
                definition,
                state.rules,
                state.visitsForCardAwards,
                state.journeys,
                owned?.revealedLevel,
            )
                .copy(earnedLevel = owned?.earnedLevel ?: 0)
        }
    }.distinctUntilChanged()

    override fun observeRewardNotices() = store.observe().map { state ->
        state.notices.map { notice ->
            val card = checkNotNull(state.card(notice.cardId))
            RewardNotice(
                notice.id,
                notice.cardId,
                card.front?.title ?: card.place?.name ?: "운전습관 카드",
                notice.fromLevel,
                notice.toLevel,
                Instant.ofEpochMilli(notice.createdAt),
            )
        }
    }.distinctUntilChanged()

    override suspend fun beginReveal(cardId: String): BeginRevealResult =
        storageResult(BeginRevealResult.StorageFailure) {
            store.transaction {
                val state = snapshot()
                val card = state.card(cardId, allowFront = true) ?: return@transaction BeginRevealResult.NotFound
                if (!card.hasPendingReveal) return@transaction BeginRevealResult.AlreadyRevealed(card)
                val target = (card.revealedLevel ?: 0) + 1
                val level = state.levels.single { it.ownershipId == card.ownershipId && it.level == target }
                BeginRevealResult.Ready(
                    RevealSession(
                        RevealToken(state.generation, card.id, card.ownershipId, level.grantId, target),
                        card.copy(earnedLevel = target),
                    ),
                )
            }
        }

    override suspend fun completeReveal(token: RevealToken): MutationResult =
        storageResult(MutationResult.StorageFailure) {
            store.transaction {
                val state = snapshot()
                if (state.generation != token.dataGeneration) return@transaction MutationResult.Stale
                val owned = state.owned.find { it.cardId == token.cardId && it.ownershipId == token.ownershipId }
                    ?: return@transaction MutationResult.Stale
                val grant = state.levels.find {
                    it.ownershipId == token.ownershipId && it.level == token.targetLevel && it.grantId == token.grantId
                } ?: return@transaction MutationResult.Stale
                if (grant.revealedAt != null) return@transaction MutationResult.AlreadyApplied
                if (token.targetLevel != (owned.revealedLevel ?: 0) + 1 || token.targetLevel > owned.earnedLevel) {
                    return@transaction MutationResult.Stale
                }
                putLevels(listOf(grant.copy(revealedAt = Instant.now().toEpochMilli())))
                putOwned(owned.copy(revealedLevel = token.targetLevel, revision = owned.revision + 1))
                touch()
                MutationResult.Success
            }
        }

    override suspend fun setRepresentativeCard(cardId: String?): MutationResult =
        storageResult(MutationResult.StorageFailure) {
            store.transaction {
                if (cardId != null && ownedCards().none { it.cardId == cardId && it.revealedLevel != null }) {
                    return@transaction MutationResult.NotFound
                }
                putDisplay(CollectionDisplayEntity(cardId = cardId))
                touch()
                MutationResult.Success
            }
        }

    override suspend fun acknowledgeRewardNotice(noticeId: String): MutationResult =
        storageResult(MutationResult.StorageFailure) {
            store.transaction {
                if (acknowledgeNotice(noticeId, Instant.now().toEpochMilli()) ==
                    0
                ) {
                    return@transaction MutationResult.AlreadyApplied
                }
                touch()
                MutationResult.Success
            }
        }
}
