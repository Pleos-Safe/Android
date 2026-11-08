package io.issac.pleosandroid.core.domain.collection.model

import java.time.Instant

data class VisitPackClaimRequest(
    val journeyId: String,
    val placeId: String,
    val placeTitle: String,
    val category: String,
    val dataGeneration: String,
)

data class VisitPackReceipt(
    val id: String,
    val journeyId: String,
    val placeId: String,
    val placeTitle: String,
    val category: String,
    val claimedAt: Instant,
)

sealed interface VisitPackClaimResult {
    data class Applied(val receipt: VisitPackReceipt) : VisitPackClaimResult
    data class AlreadyClaimed(val receipt: VisitPackReceipt) : VisitPackClaimResult
    data object DailyLimitReached : VisitPackClaimResult
    data object PlaceCooldown : VisitPackClaimResult
    data object InvalidInput : VisitPackClaimResult
    data object StaleGeneration : VisitPackClaimResult
    data object StorageFailure : VisitPackClaimResult
}
