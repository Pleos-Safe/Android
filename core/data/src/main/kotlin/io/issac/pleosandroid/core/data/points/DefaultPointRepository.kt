package io.issac.pleosandroid.core.data.points

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.storageResult
import io.issac.pleosandroid.core.data.driving.DrivingClock
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

internal class DefaultPointRepository @Inject constructor(
    private val store: CollectionStore,
    private val clock: DrivingClock,
) : PointRepository {
    override fun observeWallet() = store.observeTransaction {
        val transactions = pointTransactions().map { it.toDomain() }
        PointWallet(
            balance = transactions.sumOf { it.delta },
            transactions = transactions,
            dataGeneration = checkNotNull(userState()).generation,
        )
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

                val id = "enhancement:$operationId"
                pointTransaction(id)?.let {
                    return@transaction if (it.delta == -amount) {
                        PointSpendResult.AlreadyProcessed(it.toDomain())
                    } else {
                        PointSpendResult.ConflictingOperation
                    }
                }
                if (pointBalance() < amount) return@transaction PointSpendResult.InsufficientBalance

                val transaction = PointTransaction(
                    operationId = id,
                    sourceId = operationId,
                    kind = PointTransactionKind.EnhancementSpent,
                    delta = -amount,
                    occurredAt = clock.now(),
                ).toEntity()
                insertPointTransaction(transaction)
                touch()
                PointSpendResult.Applied(transaction.toDomain())
            }
        }
    }
}

/** Called inside the same transaction that stores a new, validated journey. */
internal suspend fun CollectionDao.creditJourney(journey: JourneyRecord) {
    val date = PointEarningPolicy.drivingDate(journey.endedAt)
    val week = PointEarningPolicy.weekStart(date).toEpochDay()
    val credit = PointEarningPolicy.settle(journey, recognizedDistanceForWeek(week)) ?: return
    insertPointTransaction(credit.toEntity())
}

private fun PointTransaction.toEntity() = PointTransactionEntity(
    operationId = operationId,
    sourceId = sourceId,
    kind = kind.name,
    delta = delta,
    occurredAt = occurredAt.toEpochMilli(),
    eligibleDistanceMeters = eligibleDistanceMeters,
    drivingEpochDay = drivingDate?.toEpochDay(),
    weekStartEpochDay = drivingDate?.let { PointEarningPolicy.weekStart(it).toEpochDay() },
    assessedScore = assessedScore,
    basePointsHundredths = basePointsHundredths,
    multiplierThousandths = multiplierThousandths,
    ruleVersion = ruleVersion,
)

private fun PointTransactionEntity.toDomain() = PointTransaction(
    operationId = operationId,
    sourceId = sourceId,
    kind = PointTransactionKind.valueOf(kind),
    delta = delta,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    eligibleDistanceMeters = eligibleDistanceMeters,
    drivingDate = drivingEpochDay?.let(LocalDate::ofEpochDay),
    assessedScore = assessedScore,
    basePointsHundredths = basePointsHundredths,
    multiplierThousandths = multiplierThousandths,
    ruleVersion = ruleVersion,
)
