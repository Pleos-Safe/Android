package io.issac.pleosandroid.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_state")
data class CatalogStateEntity(@PrimaryKey val id: Int = 1, val version: Int, val hash: String)

@Entity(tableName = "user_data_state")
data class UserDataStateEntity(
    @PrimaryKey val id: Int = 1,
    val generation: String,
    val revision: Long = 0,
    @ColumnInfo(defaultValue = "0") val demoSeeded: Boolean = false,
)

@Entity(tableName = "place", indices = [Index("active")])
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val district: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val minimumDwellSeconds: Long,
    val imageKey: String,
    val active: Boolean,
    @ColumnInfo(defaultValue = "NULL") val regionId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val rewardPack: String? = null,
    @ColumnInfo(defaultValue = "NULL") val cardPrintId: String? = null,
)

@Entity(
    tableName = "card_definition",
    foreignKeys = [ForeignKey(PlaceEntity::class, ["id"], ["placeId"])],
    indices = [Index("placeId")],
)
data class CardDefinitionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val placeId: String?,
    val title: String,
    val subtitle: String,
    val context: String,
    val series: String,
    val artworkKey: String,
    val glossKey: String,
    val engravingKey: String,
    val template: String,
    val ruleVersion: Int,
    val active: Boolean,
)

@Entity(
    tableName = "card_level_rule",
    primaryKeys = ["cardId", "level", "version"],
    foreignKeys = [ForeignKey(CardDefinitionEntity::class, ["id"], ["cardId"])],
)
data class CardLevelRuleEntity(
    val cardId: String,
    val level: Int,
    val version: Int,
    val requiredCount: Int,
    val eligibleFrom: Long,
    val minimumScore: Int?,
    val minimumDistanceMeters: Long,
    val maximumHarshStarts: Int?,
)

@Entity(tableName = "journey")
data class JourneyEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val zoneId: String,
    val distanceMeters: Long,
    val assessment: String,
    val score: Int?,
    val harshStarts: Int?,
    val assessmentVersion: Int,
    @ColumnInfo(defaultValue = "NULL") val drivingJson: String? = null,
)

/** A compact recovery checkpoint; no raw speed or location trace is retained. */
@Entity(tableName = "driving_draft")
data class DrivingDraftEntity(
    @PrimaryKey val id: Int = 1,
    val generation: String,
    val payload: String,
    val finished: Boolean,
)

@Entity(
    tableName = "visit",
    foreignKeys = [
        ForeignKey(PlaceEntity::class, ["id"], ["placeId"]),
        ForeignKey(JourneyEntity::class, ["id"], ["journeyId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("placeId", "confirmedAt"), Index("journeyId")],
)
data class VisitEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val journeyId: String?,
    val arrivedAt: Long,
    val departedAt: Long,
    val confirmedAt: Long,
    val zoneId: String,
    val distanceFromPlaceMeters: Double,
    val accuracyMeters: Double,
    val qualificationVersion: Int,
)

@Entity(tableName = "progress_operation", indices = [Index("sourceKind", "sourceId")])
data class ProgressOperationEntity(
    @PrimaryKey val id: String,
    val sourceKind: String,
    val sourceId: String,
    val sourceVersion: Int,
    val fingerprint: String?,
    val receipt: String?,
    val invalidated: Boolean = false,
)

@Entity(
    tableName = "card_memory",
    foreignKeys = [
        ForeignKey(CardDefinitionEntity::class, ["id"], ["cardId"]),
        ForeignKey(VisitEntity::class, ["id"], ["visitId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(JourneyEntity::class, ["id"], ["journeyId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("cardId", "occurredAt"), Index(value = ["cardId", "visitId"], unique = true),
        Index(value = ["cardId", "journeyId"], unique = true), Index("visitId"), Index("journeyId"),
    ],
)
data class CardMemoryEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val visitId: String?,
    val journeyId: String?,
    val occurredAt: Long,
    val localDate: String,
)

@Entity(
    tableName = "owned_card",
    foreignKeys = [ForeignKey(CardDefinitionEntity::class, ["id"], ["cardId"])],
    indices = [Index(value = ["ownershipId"], unique = true)],
)
data class OwnedCardEntity(
    @PrimaryKey val cardId: String,
    val ownershipId: String,
    val earnedLevel: Int,
    val revealedLevel: Int?,
    val acquiredAt: Long,
    val revision: Long,
)

@Entity(
    tableName = "card_level_state",
    primaryKeys = ["ownershipId", "level"],
    foreignKeys = [ForeignKey(OwnedCardEntity::class, ["ownershipId"], ["ownershipId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["grantId"], unique = true)],
)
data class CardLevelStateEntity(
    val ownershipId: String,
    val level: Int,
    val grantId: String,
    val earnedAt: Long,
    val ruleVersion: Int,
    val revealedAt: Long?,
)

@Entity(
    tableName = "reward_notice",
    foreignKeys = [ForeignKey(OwnedCardEntity::class, ["ownershipId"], ["ownershipId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ownershipId"), Index("acknowledgedAt"), Index(value = ["operationId", "cardId"], unique = true)],
)
data class RewardNoticeEntity(
    @PrimaryKey val id: String,
    val operationId: String,
    val cardId: String,
    val ownershipId: String,
    val fromLevel: Int,
    val toLevel: Int,
    val createdAt: Long,
    val acknowledgedAt: Long?,
)

@Entity(
    tableName = "collection_display",
    foreignKeys = [ForeignKey(OwnedCardEntity::class, ["cardId"], ["cardId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("cardId")],
)
data class CollectionDisplayEntity(@PrimaryKey val id: Int = 1, val cardId: String?)
