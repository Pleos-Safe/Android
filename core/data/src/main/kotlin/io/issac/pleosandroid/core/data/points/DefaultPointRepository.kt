package io.issac.pleosandroid.core.data.points

import androidx.room.withTransaction
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.storageResult
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.data.membership.toDomain
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.PointTransactionEntity
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.points.PointEarningPolicy
import io.issac.pleosandroid.core.domain.points.PointRepository
import io.issac.pleosandroid.core.domain.points.PointSpendResult
import io.issac.pleosandroid.core.domain.points.PointTransaction
import io.issac.pleosandroid.core.domain.points.PointTransactionKind
import io.issac.pleosandroid.core.domain.points.PointWallet
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.map

internal class DefaultPointRepository @Inject constructor(
    private val store: CollectionStore,
    private val clock: DrivingClock,
) : PointRepository {
    override fun observeWallet() = store.observeDatabase { db ->
        val dao = db.collectionDao()
        dao.observeRevision().map {
            db.withTransaction {
                val transactions = dao.pointTransactions().map { it.toDomain() }
                PointWallet(
                    balanceHalfPoints = transactions.sumOf { it.deltaHalfPoints },
                    transactions = transactions,
                    dataGeneration = checkNotNull(dao.userState()).generation,
                )
            }
        }
    }

    override suspend fun spendForEnhancement(
        operationId: String,
        amount: Long,
        dataGeneration: String,
    ): PointSpendResult {
        if (operationId.isBlank() || amount <= 0) return PointSpendResult.InvalidInput
        return storageResult(PointSpendResult.StorageFailure) {
            store.transaction {
                if (userState()?.generation != dataGeneration) return@transaction PointSpendResult.StaleGeneration

                if (amount > Long.MAX_VALUE / 2) return@transaction PointSpendResult.InvalidInput
                debitEnhancement(operationId, amount * 2, clock.now())
            }
        }
    }
}

/** The caller owns the transaction, including the card revision and the forge receipt. */
internal suspend fun CollectionDao.debitEnhancement(id: String, costHalfPoints: Long, at: Instant): PointSpendResult {
    require(id.isNotBlank() && costHalfPoints > 0)
    val key = "enhancement:$id"
    pointTransaction(key)?.let { prior ->
        return if (prior.deltaHalfPoints == -costHalfPoints) {
            PointSpendResult.AlreadyProcessed(prior.toDomain())
        } else {
            PointSpendResult.ConflictingOperation
        }
    }
    if (balanceHalfPoints() < costHalfPoints) return PointSpendResult.InsufficientBalance
    val debit = PointTransaction(key, id, PointTransactionKind.EnhancementSpent, -costHalfPoints, at)
    insertPointTransaction(debit.toEntity())
    touch()
    return PointSpendResult.Applied(debit)
}

internal suspend fun CollectionDao.openAlbumBalance(halfPoints: Long, at: Instant) {
    if (pointTransaction("album:opening") != null) return
    val opening = PointTransaction("album:opening", "album", PointTransactionKind.OpeningBalance, halfPoints, at)
    insertPointTransaction(opening.toEntity())
    touch()
}

/** Called inside the same transaction that stores a new, validated journey. */
internal suspend fun CollectionDao.creditJourney(journey: JourneyRecord) {
    if (pointTransaction("journey:${journey.id}") != null) return
    val terms = membershipsDuring(journey.startedAt.toEpochMilli(), journey.endedAt.toEpochMilli())
    val covered = when {
        terms.isEmpty() -> 0L
        terms.any { it.toDomain().covers(journey.startedAt, journey.endedAt) } -> journey.distanceMeters
        else -> journey.driving?.membershipDistanceMeters ?: 0L
    }
    val date = PointEarningPolicy.drivingDate(journey.endedAt)
    val week = PointEarningPolicy.weekStart(date).toEpochDay()
    val credit = PointEarningPolicy.settle(journey, recognizedDistanceForWeek(week), covered) ?: return
    insertPointTransaction(credit.toEntity())
}

private fun PointTransaction.toEntity() = PointTransactionEntity(
    operationId = operationId,
    sourceId = sourceId,
    kind = kind.name,
    deltaHalfPoints = deltaHalfPoints,
    occurredAt = occurredAt.toEpochMilli(),
    eligibleDistanceMeters = eligibleDistanceMeters,
    drivingEpochDay = drivingDate?.toEpochDay(),
    weekStartEpochDay = drivingDate?.let { PointEarningPolicy.weekStart(it).toEpochDay() },
    assessedScore = assessedScore,
    basePointsHundredths = basePointsHundredths,
    multiplierThousandths = multiplierThousandths,
    ruleVersion = ruleVersion,
    membershipBonusHalfPoints = membershipBonusHalfPoints,
)

private fun PointTransactionEntity.toDomain() = PointTransaction(
    operationId = operationId,
    sourceId = sourceId,
    kind = PointTransactionKind.valueOf(kind),
    deltaHalfPoints = deltaHalfPoints,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    eligibleDistanceMeters = eligibleDistanceMeters,
    drivingDate = drivingEpochDay?.let(LocalDate::ofEpochDay),
    assessedScore = assessedScore,
    basePointsHundredths = basePointsHundredths,
    multiplierThousandths = multiplierThousandths,
    ruleVersion = ruleVersion,
    membershipBonusHalfPoints = membershipBonusHalfPoints,
)
