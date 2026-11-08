package io.issac.pleosandroid.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "point_transaction", indices = [Index("weekStartEpochDay")])
data class PointTransactionEntity(
    @PrimaryKey val operationId: String,
    val sourceId: String,
    val kind: String,
    @ColumnInfo(name = "delta") val deltaHalfPoints: Long,
    val occurredAt: Long,
    val eligibleDistanceMeters: Long,
    val drivingEpochDay: Long?,
    val weekStartEpochDay: Long?,
    val assessedScore: Int?,
    val basePointsHundredths: Long,
    val multiplierThousandths: Int?,
    val ruleVersion: Int,
    @ColumnInfo(defaultValue = "0") val membershipBonusHalfPoints: Long = 0,
)
