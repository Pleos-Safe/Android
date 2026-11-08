package io.issac.pleosandroid.core.data.cards

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.data.collection.local.CatalogLoader
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.DefaultBadgeRegionSource
import io.issac.pleosandroid.core.data.collection.repository.DefaultCollectionDemoRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultPlaceRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultProgressRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultVisitPackRepository
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.data.points.DefaultPointRepository
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.database.entity.VisitPackEntity
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.ProgressEvent
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.driving.model.DrivingAssessment
import io.issac.pleosandroid.core.domain.points.formatHalfPoints
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardRewardsIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val real = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
    private val demo = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
    private val catalog = object : CatalogSource {
        override fun load() = CatalogLoader(context).load()
        override fun hasAsset(key: String) = true
    }
    private val provider = object : CollectionDatabaseProvider {
        override fun create(demo: Boolean) = if (demo) this@CardRewardsIntegrationTest.demo else real
    }
    private val clock = object : DrivingClock {
        override fun now(): Instant = Instant.parse("2026-11-07T04:00:00Z")
        override fun elapsedNanos() = 0L
    }
    private val firstRoll = object : Random() {
        override fun nextBits(bitCount: Int) = 0
    }
    private lateinit var store: CollectionStore

    @Before fun init() {
        context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE).edit().clear().commit()
        store = CollectionStore(context, provider, catalog)
    }

    @After fun close() {
        real.close()
        demo.close()
        context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun goyangClaimOpensOnceAndDrivingPaysForTwoForgeAttempts() = runBlocking {
        val album = DefaultCardAlbumRepository(store, firstRoll)
        val points = DefaultPointRepository(store, clock)
        val progress = DefaultProgressRepository(store)
        val visits = DefaultVisitPackRepository(store, clock)
        val generation = progress.getDataGeneration()
        val drive = JourneyRecord(
            "drive",
            clock.now().minusSeconds(
                1800,
            ),
            clock.now(), "Asia/Seoul", 25_000, AssessmentStatus.Complete, 80, 0,
            driving = DrivingAssessment(
                elapsedMillis = 1_800_000, observedMillis = 1_800_000, movingMillis = 1_800_000,
                sampleCount = 1000, rejectedSamples = 0, harshAccelerations = 10, harshBrakings = 0,
                harshStarts = 0, longestGapMillis = 0,
            ),
        )
        val result = progress.recordProgress(ProgressEvent.JourneyCompleted("trip", generation, drive, true))
        assertTrue(result.toString(), result is RecordProgressResult.Applied)
        assertEquals(3200L, points.observeWallet().first().balanceHalfPoints)

        val place = "hyundai-motorstudio-goyang"
        val request = VisitPackClaimRequest(drive.id, place, "현대 모터스튜디오 고양", "현대 관련 장소", generation)
        val claims = (1..3).map { async { visits.claim(request) } }.awaitAll()
        assertEquals(1, claims.count { it is VisitPackClaimResult.Applied })
        assertEquals(2, claims.count { it is VisitPackClaimResult.AlreadyClaimed })
        val pack = visits.observeInbox().first().single().albumPackId
        assertEquals(PackType.Hyundai, album.observe().first().inbox.single { it.id == pack }.packType)
        val copy = album.open(pack, generation)
        assertEquals(copy, album.open(pack, generation))
        val map = DefaultPlaceRepository(store)
        assertTrue(map.observePlace(place).first()!!.ownedCardIds.isEmpty())
        album.reveal(copy, generation)
        val card = album.observe().first().collection.single()
        assertEquals(CardGrade.HH, card.grade)
        assertFalse(card.variant)
        assertEquals(setOf(place), map.observePlace(place).first()!!.ownedCardIds)
        assertEquals(setOf("KR-41"), DefaultBadgeRegionSource(store).collectedRegionIds.first())

        val first = album.enhance(copy, 0, "attempt-a", generation)
        album.acknowledge(first.id, generation)
        val second = (1..3).map {
            async { album.enhance(copy, 1, "attempt-b", generation) }
        }.awaitAll().distinct().single()
        assertEquals(495L, second.costHalfPoints)
        assertEquals(2, album.observe().first().history.size)
        val wallet = points.observeWallet().first()
        assertEquals("1,127.5 P", formatHalfPoints(wallet.balanceHalfPoints))
        assertEquals(wallet.balanceHalfPoints, album.observe().first().balanceHalfPoints)
    }

    @Test fun switchingProfilesKeepsIndependentWalletsAndRejectsAnOpenFromTheOldShelf() = runBlocking {
        val album = DefaultCardAlbumRepository(store, firstRoll)
        val profiles = DefaultCollectionDemoRepository(store)
        val original = album.observe().first()
        profiles.setEnabled(true)
        val trial = album.observe().first()
        assertTrue(trial.demo)
        assertTrue(trial.inbox.any { it.packType == PackType.Hyundai })
        assertTrue(
            trial.collection.any {
                it.grade == CardGrade.H
            } && trial.collection.any { it.grade == CardGrade.HH },
        )
        val stale = runCatching { album.open("welcome:seoul", original.generation) }.exceptionOrNull()
        assertEquals(CardProblem.Changed, (stale as CardActionException).problem)
        val card = trial.collection.first { it.grade == CardGrade.H }
        album.enhance(card.id, card.revision, "demo-attempt", trial.generation)
        val saved = album.observe().first().balanceHalfPoints
        val reopened = CollectionStore(context, provider, catalog)
        assertEquals(saved, DefaultPointRepository(reopened, clock).observeWallet().first().balanceHalfPoints)
        profiles.setEnabled(false)
        assertEquals(original, album.observe().first())
        profiles.setEnabled(true)
        assertEquals(saved, album.observe().first().balanceHalfPoints)
        profiles.resetDemo()
        assertTrue(album.observe().first().history.none { it.id == "demo-attempt" })
        profiles.setEnabled(false)
        assertEquals(original, album.observe().first())
    }

    @Test fun earlierClaimCreatesOnePackEvenAfterReopeningItAndRestarting() = runBlocking {
        val album = DefaultCardAlbumRepository(store, firstRoll)
        album.observe().first()
        real.collectionDao().insertVisitPack(
            VisitPackEntity("old", "drive", "hyundai-motorstudio-goyang", "고양", "현대", 100, 0),
        )
        fun restart() = DefaultCardAlbumRepository(CollectionStore(context, provider, catalog), firstRoll)
        val upgraded = restart()
        val pack = upgraded.observe().first().inbox.single { it.id == "reward:visit:old" }
        val id = upgraded.open(pack.id)
        upgraded.reveal(id)
        val again = restart().observe().first()
        assertEquals(1, again.packs.count { it.id == pack.id })
        assertEquals(id, again.collection.single().id)
        assertFalse(again.inbox.any { it.id == pack.id })
    }
}
