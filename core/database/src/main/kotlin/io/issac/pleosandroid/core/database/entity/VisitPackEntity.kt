package io.issac.pleosandroid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "visit_pack",
    indices = [
        Index(value = ["journeyId", "placeId"], unique = true),
        Index(value = ["placeId", "claimedAt"]),
        Index("claimEpochDay"),
    ],
)
data class VisitPackEntity(
    @PrimaryKey val id: String,
    val journeyId: String,
    val placeId: String,
    val placeTitle: String,
    val category: String,
    val claimedAt: Long,
    val claimEpochDay: Long,
)
