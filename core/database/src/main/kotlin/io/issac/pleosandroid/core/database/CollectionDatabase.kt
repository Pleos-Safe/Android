package io.issac.pleosandroid.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.issac.pleosandroid.core.database.dao.AlbumDao
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.CardDefinitionEntity
import io.issac.pleosandroid.core.database.entity.CardLevelRuleEntity
import io.issac.pleosandroid.core.database.entity.CardLevelStateEntity
import io.issac.pleosandroid.core.database.entity.CardMemoryEntity
import io.issac.pleosandroid.core.database.entity.CatalogStateEntity
import io.issac.pleosandroid.core.database.entity.CollectionDisplayEntity
import io.issac.pleosandroid.core.database.entity.CopyEntity
import io.issac.pleosandroid.core.database.entity.DrivingDraftEntity
import io.issac.pleosandroid.core.database.entity.ForgeEntity
import io.issac.pleosandroid.core.database.entity.JourneyEntity
import io.issac.pleosandroid.core.database.entity.OwnedCardEntity
import io.issac.pleosandroid.core.database.entity.PackEntity
import io.issac.pleosandroid.core.database.entity.PlaceEntity
import io.issac.pleosandroid.core.database.entity.PointTransactionEntity
import io.issac.pleosandroid.core.database.entity.PrintEntity
import io.issac.pleosandroid.core.database.entity.ProgressOperationEntity
import io.issac.pleosandroid.core.database.entity.RewardNoticeEntity
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.database.entity.VisitEntity
import io.issac.pleosandroid.core.database.entity.VisitPackEntity
import io.issac.pleosandroid.core.database.entity.WalletEntity

@Database(
    entities = [
        PrintEntity::class, PackEntity::class, CopyEntity::class, WalletEntity::class, ForgeEntity::class,
        CatalogStateEntity::class, UserDataStateEntity::class, PlaceEntity::class, CardDefinitionEntity::class,
        CardLevelRuleEntity::class, JourneyEntity::class, VisitEntity::class, ProgressOperationEntity::class,
        CardMemoryEntity::class, OwnedCardEntity::class, CardLevelStateEntity::class, RewardNoticeEntity::class,
        CollectionDisplayEntity::class, DrivingDraftEntity::class, PointTransactionEntity::class,
        VisitPackEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS point_transaction (" +
                        "operationId TEXT NOT NULL, sourceId TEXT NOT NULL, kind TEXT NOT NULL, " +
                        "delta INTEGER NOT NULL, occurredAt INTEGER NOT NULL, " +
                        "eligibleDistanceMeters INTEGER NOT NULL, " +
                        "drivingEpochDay INTEGER, weekStartEpochDay INTEGER, assessedScore INTEGER, " +
                        "basePointsHundredths INTEGER NOT NULL, multiplierThousandths INTEGER, " +
                        "ruleVersion INTEGER NOT NULL, PRIMARY KEY(operationId))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_point_transaction_weekStartEpochDay " +
                        "ON point_transaction (weekStartEpochDay)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS visit_pack (id TEXT NOT NULL, journeyId TEXT NOT NULL, " +
                        "placeId TEXT NOT NULL, placeTitle TEXT NOT NULL, category TEXT NOT NULL, " +
                        "claimedAt INTEGER NOT NULL, claimEpochDay INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_visit_pack_journeyId_placeId " +
                        "ON visit_pack (journeyId, placeId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_visit_pack_placeId_claimedAt ON visit_pack (placeId, claimedAt)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_visit_pack_claimEpochDay ON visit_pack (claimEpochDay)")
            }
        }

        val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE place ADD COLUMN regionId TEXT DEFAULT NULL")
            }
        }

        val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journey ADD COLUMN drivingJson TEXT DEFAULT NULL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS driving_draft (id INTEGER NOT NULL, generation TEXT NOT NULL, " +
                        "payload TEXT NOT NULL, finished INTEGER NOT NULL, PRIMARY KEY(id))",
                )
            }
        }

        /** SQLite constraints also protect callers outside the repository implementation. */
        val constraints = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                listOf("INSERT", "UPDATE").forEach { operation ->
                    db.execSQL(
                        "CREATE TRIGGER memory_source_${operation.lowercase()} BEFORE $operation ON card_memory " +
                            "WHEN (NEW.visitId IS NULL) = (NEW.journeyId IS NULL) " +
                            "BEGIN SELECT RAISE(ABORT, 'A memory needs exactly one source'); END",
                    )
                    db.execSQL(
                        "CREATE TRIGGER owned_level_${operation.lowercase()} BEFORE $operation ON owned_card " +
                            "WHEN NEW.earnedLevel NOT BETWEEN 1 AND 5 OR " +
                            "(NEW.revealedLevel IS NOT NULL AND NEW.revealedLevel NOT BETWEEN 1 AND NEW.earnedLevel) " +
                            "BEGIN SELECT RAISE(ABORT, 'Invalid card level'); END",
                    )
                }
            }
        }
    }
}
