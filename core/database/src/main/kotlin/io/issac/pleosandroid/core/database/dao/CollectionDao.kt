package io.issac.pleosandroid.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.issac.pleosandroid.core.database.entity.CardDefinitionEntity
import io.issac.pleosandroid.core.database.entity.CardLevelRuleEntity
import io.issac.pleosandroid.core.database.entity.CardLevelStateEntity
import io.issac.pleosandroid.core.database.entity.CardMemoryEntity
import io.issac.pleosandroid.core.database.entity.CatalogStateEntity
import io.issac.pleosandroid.core.database.entity.CollectionDisplayEntity
import io.issac.pleosandroid.core.database.entity.JourneyEntity
import io.issac.pleosandroid.core.database.entity.OwnedCardEntity
import io.issac.pleosandroid.core.database.entity.PlaceEntity
import io.issac.pleosandroid.core.database.entity.PointTransactionEntity
import io.issac.pleosandroid.core.database.entity.ProgressOperationEntity
import io.issac.pleosandroid.core.database.entity.RewardNoticeEntity
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.database.entity.VisitEntity
import io.issac.pleosandroid.core.database.entity.VisitPackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM driving_draft WHERE id = 1")
    suspend fun drivingDraft(): io.issac.pleosandroid.core.database.entity.DrivingDraftEntity?

    @Upsert
    suspend fun putDrivingDraft(value: io.issac.pleosandroid.core.database.entity.DrivingDraftEntity)

    @Query("DELETE FROM driving_draft")
    suspend fun clearDrivingDraft()

    @Query("SELECT revision FROM user_data_state WHERE id = 1")
    fun observeRevision(): Flow<Long?>

    @Query("SELECT * FROM user_data_state WHERE id = 1")
    suspend fun userState(): UserDataStateEntity?

    @Upsert suspend fun putUserState(value: UserDataStateEntity)

    @Query("UPDATE user_data_state SET revision = revision + 1 WHERE id = 1")
    suspend fun touch()

    @Query("SELECT * FROM catalog_state WHERE id = 1")
    suspend fun catalogState(): CatalogStateEntity?

    @Upsert suspend fun putCatalogState(value: CatalogStateEntity)

    @Query("SELECT * FROM place ORDER BY id")
    suspend fun places(): List<PlaceEntity>

    @Query("SELECT * FROM card_definition ORDER BY id")
    suspend fun definitions(): List<CardDefinitionEntity>

    @Query("SELECT * FROM card_level_rule ORDER BY cardId, version, level")
    suspend fun rules(): List<CardLevelRuleEntity>

    @Upsert suspend fun putPlaces(values: List<PlaceEntity>)

    @Upsert suspend fun putDefinitions(values: List<CardDefinitionEntity>)

    @Insert suspend fun insertRules(values: List<CardLevelRuleEntity>)

    @Query("SELECT * FROM visit ORDER BY confirmedAt, id")
    suspend fun visits(): List<VisitEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM visit WHERE journeyId = :journeyId AND placeId = :placeId)")
    suspend fun hasRecordedVisit(journeyId: String, placeId: String): Boolean

    @Query("SELECT * FROM journey ORDER BY endedAt, id")
    suspend fun journeys(): List<JourneyEntity>

    @Insert suspend fun insertVisit(value: VisitEntity)

    @Upsert suspend fun putJourney(value: JourneyEntity)

    @Query("SELECT * FROM point_transaction ORDER BY occurredAt DESC, operationId DESC")
    suspend fun pointTransactions(): List<PointTransactionEntity>

    @Query("SELECT * FROM point_transaction WHERE operationId = :operationId")
    suspend fun pointTransaction(operationId: String): PointTransactionEntity?

    @Query("SELECT COALESCE(SUM(delta), 0) FROM point_transaction")
    suspend fun pointBalance(): Long

    @Query("SELECT COALESCE(SUM(eligibleDistanceMeters), 0) FROM point_transaction WHERE weekStartEpochDay = :week")
    suspend fun recognizedDistanceForWeek(week: Long): Long

    @Insert suspend fun insertPointTransaction(value: PointTransactionEntity)

    @Query(
        "UPDATE point_transaction SET assessedScore = NULL, multiplierThousandths = NULL WHERE operationId = :operationId",
    )
    suspend fun redactPointAssessment(operationId: String)

    @Query("DELETE FROM point_transaction")
    suspend fun clearPointTransactions()

    @Query("SELECT * FROM visit_pack ORDER BY claimedAt DESC, id DESC")
    suspend fun visitPacks(): List<VisitPackEntity>

    @Query("SELECT * FROM visit_pack WHERE journeyId = :journeyId AND placeId = :placeId")
    suspend fun visitPack(journeyId: String, placeId: String): VisitPackEntity?

    @Query("SELECT COUNT(*) FROM visit_pack WHERE claimEpochDay = :day")
    suspend fun visitPacksClaimedOn(day: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM visit_pack WHERE placeId = :placeId AND claimedAt > :cutoff)")
    suspend fun wasVisitPackClaimedAfter(placeId: String, cutoff: Long): Boolean

    @Insert suspend fun insertVisitPack(value: VisitPackEntity)

    @Query("DELETE FROM visit_pack")
    suspend fun clearVisitPacks()

    @Query("SELECT * FROM progress_operation WHERE id = :id")
    suspend fun operation(id: String): ProgressOperationEntity?

    @Query("SELECT * FROM progress_operation WHERE sourceKind = :kind AND sourceId = :sourceId")
    suspend fun sourceOperations(kind: String, sourceId: String): List<ProgressOperationEntity>

    @Insert suspend fun insertOperation(value: ProgressOperationEntity)

    @Query("UPDATE progress_operation SET receipt = NULL")
    suspend fun redactReceipts()

    @Query(
        "UPDATE progress_operation SET invalidated = 1, fingerprint = NULL, receipt = NULL WHERE sourceKind = :kind AND sourceId = :sourceId",
    )
    suspend fun invalidateSource(kind: String, sourceId: String)

    @Query("SELECT * FROM owned_card ORDER BY acquiredAt, cardId")
    suspend fun ownedCards(): List<OwnedCardEntity>

    @Query("SELECT * FROM card_level_state ORDER BY ownershipId, level")
    suspend fun levels(): List<CardLevelStateEntity>

    @Query("SELECT * FROM card_memory ORDER BY occurredAt, id")
    suspend fun memories(): List<CardMemoryEntity>

    @Query("SELECT * FROM collection_display WHERE id = 1")
    suspend fun display(): CollectionDisplayEntity?

    @Query("SELECT * FROM reward_notice WHERE acknowledgedAt IS NULL ORDER BY createdAt, id")
    suspend fun notices(): List<RewardNoticeEntity>

    @Upsert suspend fun putOwned(value: OwnedCardEntity)

    @Upsert suspend fun putLevels(values: List<CardLevelStateEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putMemories(values: List<CardMemoryEntity>)

    @Insert suspend fun insertNotice(value: RewardNoticeEntity)

    @Upsert suspend fun putDisplay(value: CollectionDisplayEntity)

    @Query("UPDATE reward_notice SET acknowledgedAt = :at WHERE id = :id AND acknowledgedAt IS NULL")
    suspend fun acknowledgeNotice(id: String, at: Long): Int

    @Query("DELETE FROM reward_notice WHERE ownershipId = :ownershipId AND toLevel > :level")
    suspend fun removeInvalidNotices(ownershipId: String, level: Int)

    @Query("DELETE FROM card_level_state WHERE ownershipId = :ownershipId AND level > :level")
    suspend fun removeHigherLevels(ownershipId: String, level: Int)

    @Query("DELETE FROM owned_card WHERE cardId = :cardId")
    suspend fun deleteOwned(cardId: String)

    @Query("DELETE FROM card_memory WHERE cardId = :cardId")
    suspend fun deleteMemories(cardId: String)

    @Query("DELETE FROM visit WHERE id = :id")
    suspend fun deleteVisit(id: String): Int

    @Query("DELETE FROM journey WHERE id = :id")
    suspend fun deleteJourney(id: String): Int

    @Query("DELETE FROM owned_card")
    suspend fun clearOwned()

    @Query("DELETE FROM visit")
    suspend fun clearVisits()

    @Query("DELETE FROM journey")
    suspend fun clearJourneys()

    @Query("DELETE FROM progress_operation")
    suspend fun clearOperations()
}
