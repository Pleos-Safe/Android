package io.issac.pleosandroid.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrivingMigrationTest {
    @get:Rule val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CollectionDatabase::class.java)

    @Test fun migrationPreservesExistingJourneysAndAddsAnEmptyCheckpoint() = runBlocking {
        val name = "driving-migration-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO journey VALUES ('existing', 1, 2, 'Asia/Seoul', 1200, 'Complete', 88, 0, 1)")
            execSQL("INSERT INTO place VALUES ('old-place', '기존 장소', '부산', '해변', 35.15, 129.16, 250, 60, 'art', 1)")
            close()
        }
        helper.runMigrationsAndValidate(
            name,
            3,
            true,
            CollectionDatabase.migration1To2,
            CollectionDatabase.migration2To3,
        ).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, CollectionDatabase::class.java, name)
            .addMigrations(CollectionDatabase.migration1To2, CollectionDatabase.migration2To3).build()
        try {
            val journey = db.collectionDao().journeys().single()
            assertEquals("existing", journey.id)
            assertEquals(88, journey.score)
            assertNull(journey.drivingJson)
            assertNull(db.collectionDao().drivingDraft())
            assertEquals("기존 장소", db.collectionDao().places().single().name)
            assertNull(db.collectionDao().places().single().regionId)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
