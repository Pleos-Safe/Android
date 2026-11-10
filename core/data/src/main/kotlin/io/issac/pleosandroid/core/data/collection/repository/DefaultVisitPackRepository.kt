package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.cards.grantVisitPack
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.database.entity.VisitPackEntity
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackPolicy
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
            store.databaseTransaction {
                val album = albumDao()
                with(collectionDao()) {
                    if (userState()?.generation != request.dataGeneration) {
                        return@databaseTransaction VisitPackClaimResult.StaleGeneration
                    }
                    visitPack(request.journeyId, request.placeId)?.let {
                        return@databaseTransaction VisitPackClaimResult.AlreadyClaimed(it.toReceipt())
                    }
                    if (hasRecordedVisit(request.journeyId, request.placeId)) {
                        return@databaseTransaction VisitPackClaimResult.InvalidInput
                    }
                    val place = place(request.placeId) ?: return@databaseTransaction VisitPackClaimResult.InvalidInput
                    val type = place.rewardPack ?: return@databaseTransaction VisitPackClaimResult.InvalidInput
                    val now = clock.now()
                    val day = now.atZone(claimZone).toLocalDate().toEpochDay()
                    val claimedToday = visitPacksClaimedOn(day)
                    if (claimedToday >= VisitPackPolicy.DAILY_CLAIM_LIMIT) {
                        return@databaseTransaction VisitPackClaimResult.DailyLimitReached
                    }
                    val cutoff = now.minus(regionCooldown).toEpochMilli()
                    if (wasVisitPackClaimedInRegionAfter(place.id, place.regionId, cutoff)) {
                        return@databaseTransaction VisitPackClaimResult.RegionCooldown
                    }
                    val pack = VisitPackEntity(
                        id = UUID.randomUUID().toString(),
                        journeyId = request.journeyId,
                        placeId = request.placeId,
                        placeTitle = place.name,
                        category = place.category,
                        claimedAt = now.toEpochMilli(),
                        claimEpochDay = day,
                    )
                    insertVisitPack(pack)
                    album.grantVisitPack(pack, type)
                    touch()
                    VisitPackClaimResult.Applied(pack.toReceipt())
                }
            }
        }
    }

    private companion object {
        val claimZone: ZoneId = ZoneId.of("Asia/Seoul")
        val regionCooldown: Duration = Duration.ofDays(VisitPackPolicy.REGION_COOLDOWN_DAYS)
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
