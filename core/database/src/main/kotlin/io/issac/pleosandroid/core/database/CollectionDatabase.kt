package io.issac.pleosandroid.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.CardDefinitionEntity
import io.issac.pleosandroid.core.database.entity.CardLevelRuleEntity
import io.issac.pleosandroid.core.database.entity.CardLevelStateEntity
import io.issac.pleosandroid.core.database.entity.CardMemoryEntity
import io.issac.pleosandroid.core.database.entity.CatalogStateEntity
import io.issac.pleosandroid.core.database.entity.CollectionDisplayEntity
import io.issac.pleosandroid.core.database.entity.DrivingDraftEntity
import io.issac.pleosandroid.core.database.entity.JourneyEntity
import io.issac.pleosandroid.core.database.entity.OwnedCardEntity
import io.issac.pleosandroid.core.database.entity.PlaceEntity
import io.issac.pleosandroid.core.database.entity.ProgressOperationEntity
import io.issac.pleosandroid.core.database.entity.RewardNoticeEntity
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.database.entity.VisitEntity

@Database(
    entities = [
        CatalogStateEntity::class, UserDataStateEntity::class, PlaceEntity::class, CardDefinitionEntity::class,
        CardLevelRuleEntity::class, JourneyEntity::class, VisitEntity::class, ProgressOperationEntity::class,
        CardMemoryEntity::class, OwnedCardEntity::class, CardLevelStateEntity::class, RewardNoticeEntity::class,
        CollectionDisplayEntity::class, DrivingDraftEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao

    companion object {
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
