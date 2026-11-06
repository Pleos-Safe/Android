package io.issac.pleosandroid.core.domain.collection.model

import java.time.Instant
import java.time.LocalDate

data class CardMemory(
    val id: String,
    val occurredAt: Instant,
    val localDate: LocalDate,
    val visitId: String?,
    val journeyId: String?,
)

/** Front metadata is absent before the first reveal; a reveal session explicitly grants access. */
data class CollectedCard(
    val id: String,
    val ownershipId: String,
    val kind: CardKind,
    val place: Place?,
    val front: CardFront?,
    val earnedLevel: Int,
    val revealedLevel: Int?,
    val acquiredAt: Instant,
    val memories: List<CardMemory>,
) {
    val hasPendingReveal get() = (revealedLevel ?: 0) < earnedLevel
}

data class CollectionSnapshot(
    val cards: List<CollectedCard>,
    val representativeId: String?,
    val pendingNoticeCount: Int,
) {
    val pendingCards get() = cards.filter { it.hasPendingReveal }
}

data class RevealToken(
    val dataGeneration: String,
    val cardId: String,
    val ownershipId: String,
    val grantId: String,
    val targetLevel: Int,
)
data class RevealSession(val token: RevealToken, val card: CollectedCard)
sealed interface BeginRevealResult {
    data class Ready(val session: RevealSession) : BeginRevealResult
    data class AlreadyRevealed(val card: CollectedCard) : BeginRevealResult
    data object NotFound : BeginRevealResult
    data object Restricted : BeginRevealResult
    data object StorageFailure : BeginRevealResult
}

data class RewardNotice(
    val id: String,
    val cardId: String,
    val label: String,
    val fromLevel: Int,
    val toLevel: Int,
    val createdAt: Instant,
)
