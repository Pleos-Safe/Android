package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.database.entity.VisitPackEntity
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackReceipt
import io.issac.pleosandroid.core.domain.collection.repository.VisitPackRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

internal class DefaultVisitPackRepository @Inject constructor(
    private val store: CollectionStore,
    private val clock: DrivingClock,
) : VisitPackRepository {
    override fun observeInbox() = store.observeTransaction { visitPacks().map { it.toReceipt() } }

    override suspend fun claim(request: VisitPackClaimRequest): VisitPackClaimResult {
        if (request.journeyId.isBlank() || request.placeId.isBlank() ||
            request.placeTitle.isBlank() || request.category.isBlank()
        ) {
            return VisitPackClaimResult.InvalidInput
        }
        return storageResult(VisitPackClaimResult.StorageFailure) {
            store.transaction {
                if (userState()?.generation != request.dataGeneration) {
                    return@transaction VisitPackClaimResult.StaleGeneration
                }
                visitPack(request.journeyId, request.placeId)?.let {
                    return@transaction VisitPackClaimResult.AlreadyClaimed(it.toReceipt())
                }
                if (hasRecordedVisit(request.journeyId, request.placeId)) {
                    return@transaction VisitPackClaimResult.InvalidInput
                }
                val now = clock.now()
                val day = now.atZone(claimZone).toLocalDate().toEpochDay()
                if (visitPacksClaimedOn(day) >= DAILY_LIMIT) return@transaction VisitPackClaimResult.DailyLimitReached
                if (wasVisitPackClaimedAfter(request.placeId, now.minus(placeCooldown).toEpochMilli())) {
                    return@transaction VisitPackClaimResult.PlaceCooldown
                }
                val pack = VisitPackEntity(
                    id = UUID.randomUUID().toString(),
                    journeyId = request.journeyId,
                    placeId = request.placeId,
                    placeTitle = request.placeTitle,
                    category = request.category,
                    claimedAt = now.toEpochMilli(),
                    claimEpochDay = day,
                )
                insertVisitPack(pack)
                touch()
                VisitPackClaimResult.Applied(pack.toReceipt())
            }
        }
    }

    private companion object {
        const val DAILY_LIMIT = 3
        val claimZone: ZoneId = ZoneId.of("Asia/Seoul")
        val placeCooldown: Duration = Duration.ofDays(14)
    }
}

private fun VisitPackEntity.toReceipt() = VisitPackReceipt(
    id = id,
    journeyId = journeyId,
    placeId = placeId,
    placeTitle = placeTitle,
    category = category,
    claimedAt = Instant.ofEpochMilli(claimedAt),
)
