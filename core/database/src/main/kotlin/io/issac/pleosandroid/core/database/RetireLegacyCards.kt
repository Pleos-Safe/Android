package io.issac.pleosandroid.core.database

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject

/** v4 imported unsupported prints under "legacy:". A legacy origin on a *regional* print is still valid. */
class RetireLegacyCards : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TEMP TABLE retired_copies AS SELECT id, packId FROM album_copy WHERE printId GLOB 'legacy:*'",
        )
        val copies = "SELECT id FROM retired_copies"
        db.execSQL("UPDATE album_wallet SET featuredId = NULL WHERE featuredId IN ($copies)")
        db.execSQL("DELETE FROM album_action WHERE copyId IN ($copies)")
        db.execSQL("UPDATE album_action SET materialId = NULL WHERE materialId IN ($copies)")
        db.execSQL("DELETE FROM album_copy WHERE id IN ($copies)")
        db.execSQL("DELETE FROM album_pack WHERE id IN (SELECT packId FROM retired_copies)")
        db.execSQL("DELETE FROM album_print WHERE id GLOB 'legacy:*'")
        db.execSQL("DROP TABLE retired_copies")

        // Removing a card does not remove the source visit or driving report.
        val retired = setOf(
            "place-igidae-001",
            "place-cheongsapo-001",
            "place-huinnyeoul-001",
            "place-songdo-001",
            "safety-smooth-001",
            "safety-gentle-start-001",
        )
        val ids = retired.toTypedArray()
        val match = ids.joinToString(",") { "?" }
        val notices = db.query("SELECT id FROM reward_notice WHERE cardId IN ($match)", ids).use { rows ->
            buildSet { while (rows.moveToNext()) add(rows.getString(0)) }
        }
        pruneReceipts(db, retired, notices)
        db.execSQL("UPDATE collection_display SET cardId = NULL WHERE cardId IN ($match)", ids)
        db.execSQL("DELETE FROM reward_notice WHERE cardId IN ($match)", ids)
        db.execSQL(
            "DELETE FROM card_level_state WHERE ownershipId IN (SELECT ownershipId FROM owned_card WHERE cardId IN ($match))",
            ids,
        )
        for (table in listOf("card_memory", "card_level_rule", "owned_card")) {
            db.execSQL("DELETE FROM $table WHERE cardId IN ($match)", ids)
        }
        db.execSQL("DELETE FROM card_definition WHERE id IN ($match)", ids)
    }

    /** Preserve the operation identity and the surviving entries of mixed reward receipts on replay. */
    private fun pruneReceipts(db: SupportSQLiteDatabase, retired: Set<String>, notices: Set<String>) {
        db.query("SELECT id, receipt FROM progress_operation WHERE receipt IS NOT NULL ORDER BY id").use { rows ->
            while (rows.moveToNext()) {
                val receipt = JSONObject(rows.getString(1))
                var changed = false
                for (key in listOf("changes", "progress", "notices")) {
                    val items = receipt.optJSONArray(key) ?: continue
                    for (index in items.length() - 1 downTo 0) {
                        val remove = if (key == "notices") {
                            items.optString(index) in notices
                        } else {
                            items.optJSONObject(index)?.optString("id") in retired
                        }
                        if (remove) {
                            items.remove(index)
                            changed = true
                        }
                    }
                }
                if (changed) {
                    db.execSQL(
                        "UPDATE progress_operation SET receipt = ? WHERE id = ?",
                        arrayOf(receipt.toString(), rows.getString(0)),
                    )
                }
            }
        }
    }
}
