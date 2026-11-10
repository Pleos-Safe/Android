package io.issac.pleosandroid.core.domain.collection.model

import java.time.Instant

object VisitPackPolicy {
    const val DAILY_CLAIM_LIMIT = 10
    const val REGION_COOLDOWN_DAYS = 14L
}

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
) {
    val albumPackId: String get() = visitAlbumPackId(id)
}

fun visitAlbumPackId(receiptId: String): String = "reward:visit:$receiptId"

sealed interface VisitPackClaimResult {
    data class Applied(val receipt: VisitPackReceipt) : VisitPackClaimResult
    data class AlreadyClaimed(val receipt: VisitPackReceipt) : VisitPackClaimResult
    data object DailyLimitReached : VisitPackClaimResult
    data object RegionCooldown : VisitPackClaimResult
    data object InvalidInput : VisitPackClaimResult
    data object StaleGeneration : VisitPackClaimResult
    data object StorageFailure : VisitPackClaimResult
}
