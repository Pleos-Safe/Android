package io.issac.pleosandroid.core.domain.points

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

enum class PointTransactionKind { JourneyEarned, EnhancementSpent }

data class PointTransaction(
    val operationId: String,
    val sourceId: String,
    val kind: PointTransactionKind,
    val delta: Long,
    val occurredAt: Instant,
    val eligibleDistanceMeters: Long = 0,
    val drivingDate: LocalDate? = null,
    val assessedScore: Int? = null,
    val basePointsHundredths: Long = 0,
    val multiplierThousandths: Int? = null,
    val ruleVersion: Int = 1,
)

data class PointWallet(
    val balance: Long = 0,
    val transactions: List<PointTransaction> = emptyList(),
    val dataGeneration: String? = null,
)

sealed interface PointSpendResult {
    data class Applied(val transaction: PointTransaction) : PointSpendResult
    data class AlreadyProcessed(val transaction: PointTransaction) : PointSpendResult
    data object InsufficientBalance : PointSpendResult
    data object ConflictingOperation : PointSpendResult
    data object InvalidInput : PointSpendResult
    data object StaleGeneration : PointSpendResult
    data object StorageFailure : PointSpendResult
}

interface PointRepository {
    fun observeWallet(): Flow<PointWallet>

    /** Repeat a spend with the original operation ID and wallet generation. */
    suspend fun spendForEnhancement(operationId: String, amount: Long, dataGeneration: String): PointSpendResult
}
