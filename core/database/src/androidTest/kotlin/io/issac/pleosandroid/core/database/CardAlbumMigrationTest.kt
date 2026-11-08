package io.issac.pleosandroid.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardAlbumMigrationTest {
    @get:Rule val migrations =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CollectionDatabase::class.java)

    @Test fun versionThreeOwnershipAndDisplaySurviveAlongsideTheNewAlbum() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "regional-album-migration"
        migrations.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO card_definition VALUES " +
                    "('old', 'Safety', NULL, '귀가', '', '', 'series', 'image', 'gloss', 'ink', 'Standard', 1, 1)",
            )
            execSQL("INSERT INTO owned_card VALUES ('old', 'my-copy', 4, 2, 1500, 6)")
            execSQL("INSERT INTO collection_display VALUES (1, 'old')")
            close()
        }
        try {
            migrations.runMigrationsAndValidate(
                name,
                7,
                true,
                CollectionDatabase.migration3To4,
                CardRewardsMigration.from4,
                CollectionDatabase.migration6To7,
            ).close()
            val db = Room.databaseBuilder(context, CollectionDatabase::class.java, name).build()
            try {
                assertEquals("my-copy", db.collectionDao().ownedCards().single().ownershipId)
                assertEquals("old", db.collectionDao().display()?.cardId)
                assertNull(db.albumDao().wallet())
                assertEquals(emptyList<Any>(), db.albumDao().copies())
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun versionFourRetiresUnillustratedCardsWithoutErasingTheTripOrRerollingRealPacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "regional-retirement"
        val keep = "place-haeundae-001"
        val retire = "place-igidae-001"
        val fakePrint = "legacy:$retire"
        val receipt = """
            {"changes":[{"id":"$keep"},{"id":"$retire"}],
             "progress":[{"id":"$retire"},{"id":"$keep"}],
             "notices":["notice-bad","notice-good"]}
        """.trimIndent()
        try {
            // Card v4 and v5 shared the same physical schema. The exported 4.json belongs to main.
            migrations.createDatabase(name, 5).use { db ->
                db.version = 4
                db.execSQL("INSERT INTO place VALUES ('igidae','이기대','','',35,129,150,60,'photo',1,'KR-26')")
                db.execSQL("INSERT INTO journey VALUES ('drive',0,100,'Asia/Seoul',5000,'Pending',NULL,NULL,1,NULL)")
                db.execSQL("INSERT INTO visit VALUES ('visit','igidae','drive',0,100,100,'Asia/Seoul',1,1,1)")
                db.execSQL(
                    "INSERT INTO progress_operation VALUES ('event','visit','visit',1,'fingerprint',?,0)",
                    arrayOf(receipt),
                )
                for ((id, owner) in listOf(keep to "good", retire to "bad")) {
                    db.execSQL(
                        "INSERT INTO card_definition VALUES (?, 'Place', NULL,'','','','','art','gloss','ink','Standard',1,1)",
                        arrayOf(id),
                    )
                    db.execSQL("INSERT INTO owned_card VALUES (?, ?,2,2,100,1)", arrayOf(id, owner))
                    db.execSQL("INSERT INTO card_level_state VALUES (?,1,?,100,1,100)", arrayOf(owner, "grant-$owner"))
                    db.execSQL("INSERT INTO card_level_rule VALUES (?,1,1,1,0,NULL,0,NULL)", arrayOf(id))
                    db.execSQL(
                        "INSERT INTO reward_notice VALUES (?,'event',?,?,1,2,100,NULL)",
                        arrayOf("notice-$owner", id, owner),
                    )
                    db.execSQL(
                        "INSERT INTO card_memory VALUES (?,?,'visit',NULL,100,'2026-11-07')",
                        arrayOf("memory-$owner", id),
                    )
                    val print = if (id == keep) "busan-haeundae" else fakePrint
                    db.execSQL(
                        "INSERT INTO album_print VALUES (?,'','Busan','Beach','Common','art','variant','','')",
                        arrayOf(print),
                    )
                    db.execSQL(
                        "INSERT INTO album_pack VALUES (?,'Busan','이전 컬렉션',100,'장소',NULL)",
                        arrayOf("legacy:$owner"),
                    )
                    db.execSQL(
                        "INSERT INTO album_copy VALUES (?,?,?,0,5,5,'Owned',1,100,100,?)",
                        arrayOf(owner, "legacy:$owner", print, id),
                    )
                    db.execSQL(
                        "INSERT INTO album_action VALUES (?,?,0,5,'LegacyAdvance',0,100,0,NULL)",
                        arrayOf("act-$owner", owner),
                    )
                }
                db.execSQL("UPDATE album_action SET materialId = 'bad' WHERE id = 'act-good'")
                db.execSQL("INSERT INTO album_pack VALUES ('granted-unopened','Jeju','방문 보상',200,NULL,'drive')")
                db.execSQL("INSERT INTO collection_display VALUES (1,?)", arrayOf(retire))
                db.execSQL("INSERT INTO album_wallet VALUES (1,987,'bad')")
            }
            migrations.runMigrationsAndValidate(name, 6, true, CardRewardsMigration.from4).use { db ->
                assertEquals("good", db.one("SELECT id FROM album_copy"))
                assertEquals("busan-haeundae", db.one("SELECT id FROM album_print"))
                assertEquals("act-good", db.one("SELECT id FROM album_action"))
                assertNull(db.one("SELECT materialId FROM album_action"))
                assertEquals("legacy:good", db.one("SELECT id FROM album_pack WHERE id LIKE 'legacy:%'"))
                assertEquals("granted-unopened", db.one("SELECT id FROM album_pack WHERE id NOT LIKE 'legacy:%'"))
                assertEquals("987", db.one("SELECT delta FROM point_transaction WHERE operationId = 'album:opening'"))
                assertNull(db.one("SELECT featuredId FROM album_wallet"))
                assertNull(db.one("SELECT cardId FROM collection_display"))
                assertEquals(keep, db.one("SELECT id FROM card_definition"))
                for (table in listOf("card_level_rule", "card_memory", "owned_card", "reward_notice")) {
                    assertEquals(keep, db.one("SELECT cardId FROM $table"))
                }
                assertEquals("good", db.one("SELECT ownershipId FROM card_level_state"))
                assertEquals("이기대", db.one("SELECT name FROM place"))
                assertEquals("drive", db.one("SELECT journeyId FROM visit"))
                assertEquals("drive", db.one("SELECT id FROM journey"))
                assertEquals("fingerprint", db.one("SELECT fingerprint FROM progress_operation"))
                val cleaned = JSONObject(db.one("SELECT receipt FROM progress_operation")!!)
                for (key in listOf("changes", "progress")) {
                    val entries = cleaned.getJSONArray(key)
                    assertEquals(1, entries.length())
                    assertEquals(keep, entries.getJSONObject(0).getString("id"))
                }
                assertEquals("""["notice-good"]""", cleaned.getJSONArray("notices").toString())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun drivingV4PreservesPointsAndClaimAndCardV5PreservesItsFractionalWallet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in listOf(4, 5)) {
            val name = "rewards-migration-$version"
            try {
                migrations.createDatabase(name, version).use { db ->
                    if (version == 4) {
                        db.execSQL(
                            "INSERT INTO point_transaction (operationId, sourceId, kind, delta, occurredAt, " +
                                "eligibleDistanceMeters, basePointsHundredths, ruleVersion) " +
                                "VALUES ('journey:j', 'j', 'JourneyEarned', 600, 100, 25000, 50000, 1)",
                        )
                        db.execSQL(
                            "INSERT INTO visit_pack VALUES ('r','j','hyundai-motorstudio-goyang','고양','현대',100,0)",
                        )
                    } else {
                        db.execSQL("INSERT INTO album_wallet VALUES (1,705,NULL)")
                    }
                }
                migrations.runMigrationsAndValidate(
                    name,
                    7,
                    true,
                    CardRewardsMigration.from4,
                    CardRewardsMigration.from5,
                    CollectionDatabase.migration6To7,
                ).use { db ->
                    assertEquals(
                        if (version ==
                            4
                        ) {
                            "1200"
                        } else {
                            "705"
                        },
                        db.one("SELECT SUM(delta) FROM point_transaction"),
                    )
                    assertEquals("0", db.one("SELECT SUM(membershipBonusHalfPoints) FROM point_transaction"))
                    assertEquals("0", db.one("SELECT COUNT(*) FROM membership_term"))
                    if (version == 4) assertEquals("r", db.one("SELECT id FROM visit_pack"))
                }
            } finally {
                context.deleteDatabase(name)
            }
        }
    }

    private fun SupportSQLiteDatabase.one(query: String): String? = query(query).use {
        assertEquals(query, 1, it.count)
        it.moveToFirst()
        if (it.isNull(0)) null else it.getString(0)
    }
}
