package io.issac.pleosandroid.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The driving and card versions of v4 have different tables. Both upgrade directly to v6. */
object CardRewardsMigration {
    val from4 = migration(4)
    val from5 = migration(5)

    private fun migration(from: Int) = object : Migration(from, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            CollectionDatabase.migration3To4.migrate(db)
            createAlbumTables(db)
            db.execSQL("UPDATE point_transaction SET delta = delta * 2")
            // Import the net wallet once. Its individual prior charges remain in album_action.
            db.execSQL(
                "INSERT INTO point_transaction (operationId, sourceId, kind, delta, occurredAt, " +
                    "eligibleDistanceMeters, basePointsHundredths, ruleVersion) " +
                    "SELECT 'album:opening', 'album', 'OpeningBalance', halfPoints, " +
                    "COALESCE((SELECT receivedAt FROM album_pack WHERE id = 'welcome:seoul'), 0), 0, 0, 1 " +
                    "FROM album_wallet WHERE id = 1",
            )
            RetireLegacyCards().onPostMigrate(db)
            db.execSQL("ALTER TABLE album_wallet RENAME TO old_album_wallet")
            db.execSQL("CREATE TABLE album_wallet (id INTEGER NOT NULL PRIMARY KEY, featuredId TEXT)")
            db.execSQL("INSERT INTO album_wallet SELECT id, featuredId FROM old_album_wallet")
            db.execSQL("DROP TABLE old_album_wallet")
            db.execSQL("ALTER TABLE user_data_state ADD COLUMN demoSeeded INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE album_print ADD COLUMN mapRegionId TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE place ADD COLUMN rewardPack TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE place ADD COLUMN cardPrintId TEXT DEFAULT NULL")
            db.execSQL("UPDATE place SET regionId = 'KR-49' WHERE regionId = 'KR-50'")
        }
    }

    private fun createAlbumTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS album_print (id TEXT NOT NULL, title TEXT NOT NULL, region TEXT, " +
                "pool TEXT NOT NULL, baseGrade TEXT NOT NULL, art TEXT NOT NULL, variantArt TEXT NOT NULL, place " +
                "TEXT NOT NULL, serial TEXT NOT NULL, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS album_pack (id TEXT NOT NULL, region TEXT, source TEXT NOT NULL, " +
                "receivedAt INTEGER NOT NULL, place TEXT, journeyId TEXT, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS album_copy (id TEXT NOT NULL, packId TEXT NOT NULL, printId TEXT NOT " +
                "NULL, variant INTEGER NOT NULL, level INTEGER NOT NULL, highest INTEGER NOT NULL, status TEXT " +
                "NOT NULL, revision INTEGER NOT NULL, acquiredAt INTEGER NOT NULL, seenAt INTEGER, legacyId " +
                "TEXT, PRIMARY KEY(id), FOREIGN KEY(printId) REFERENCES album_print(id) ON UPDATE NO ACTION ON " +
                "DELETE NO ACTION , FOREIGN KEY(packId) REFERENCES album_pack(id) ON UPDATE NO ACTION ON DELETE " +
                "NO ACTION )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_album_copy_printId ON album_copy (printId)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_album_copy_packId ON album_copy (packId)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS album_wallet (id INTEGER NOT NULL, halfPoints INTEGER NOT NULL, " +
                "featuredId TEXT, PRIMARY KEY(id))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS album_action (id TEXT NOT NULL, copyId TEXT NOT NULL, beforeLevel " +
                "INTEGER NOT NULL, afterLevel INTEGER NOT NULL, outcome TEXT NOT NULL, cost INTEGER NOT NULL, at " +
                "INTEGER NOT NULL, shown INTEGER NOT NULL, materialId TEXT, PRIMARY KEY(id), FOREIGN KEY(copyId) " +
                "REFERENCES album_copy(id) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_album_action_copyId ON album_action (copyId)",
        )
    }
}
