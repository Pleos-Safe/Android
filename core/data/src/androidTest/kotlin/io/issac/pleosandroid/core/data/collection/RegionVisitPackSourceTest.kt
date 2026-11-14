package io.issac.pleosandroid.core.data.collection

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.data.cards.DefaultCardAlbumRepository
import io.issac.pleosandroid.core.data.collection.local.CatalogLoader
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.repository.DefaultProgressRepository
import io.issac.pleosandroid.core.data.collection.repository.DefaultRegionVisitPackSource
import io.issac.pleosandroid.core.data.collection.repository.DefaultVisitPackRepository
import io.issac.pleosandroid.core.data.driving.DrivingClock
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.database.entity.VisitPackEntity
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegionVisitPackSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val real = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
    private val catalog = object : CatalogSource {
        override fun load() = CatalogLoader(context).load()
        override fun hasAsset(key: String) = true
    }
    private val provider = CollectionDatabaseProvider { real }
    private var now = Instant.parse("2026-11-07T04:00:00Z")
    private val clock = object : DrivingClock {
        override fun now() = now
        override fun elapsedNanos() = 0L
    }
    private val firstRoll = object : Random() {
        override fun nextBits(bitCount: Int) = 0
    }
    private lateinit var store: CollectionStore

    @Before fun init() {
        store = CollectionStore(provider, catalog)
    }

    @After fun close() {
        real.close()
    }

    @Test fun claimsCountBeforeOpeningAndSurviveOpeningWhileGiftsAndShopPacksDoNotCount() = runBlocking {
        val album = DefaultCardAlbumRepository(store, firstRoll)
        val regions = DefaultRegionVisitPackSource(store)
        val initial = album.observe().first()
        val generation = checkNotNull(initial.generation)
        assertTrue(regions.regionVisitPackCounts.first().isEmpty())

        val gift = album.open("welcome:seoul", generation)
        album.reveal(gift, generation)
        val purchased = album.receivePack("shop:busan", PackType.Busan, "상점", null, null)
        val purchasedCard = album.open(purchased, generation)
        album.reveal(purchasedCard, generation)
        assertTrue(regions.regionVisitPackCounts.first().isEmpty())

        val visits = DefaultVisitPackRepository(store, clock)
        val request = VisitPackClaimRequest(
            "first-drive",
            "hyundai-motorstudio-goyang",
            "현대 모터스튜디오 고양",
            "현대 관련 장소",
            generation,
        )
        val claim = visits.claim(request) as VisitPackClaimResult.Applied
        assertEquals(mapOf("KR-41" to 1), regions.regionVisitPackCounts.first())
        assertTrue(album.observe().first().inbox.any { it.id == claim.receipt.albumPackId })
        assertTrue(visits.claim(request) is VisitPackClaimResult.AlreadyClaimed)
        assertEquals(mapOf("KR-41" to 1), regions.regionVisitPackCounts.first())

        val card = album.open(claim.receipt.albumPackId, generation)
        assertEquals(mapOf("KR-41" to 1), regions.regionVisitPackCounts.first())
        album.reveal(card, generation)
        assertEquals(mapOf("KR-41" to 1), regions.regionVisitPackCounts.first())
        store.albumTransaction { clearCopies() }
        assertEquals(mapOf("KR-41" to 1), regions.regionVisitPackCounts.first())

        now = now.plusSeconds(15 * 86_400L)
        assertTrue(visits.claim(request.copy(journeyId = "second-drive")) is VisitPackClaimResult.Applied)
        assertEquals(mapOf("KR-41" to 2), regions.regionVisitPackCounts.first())
    }

    @Test fun countsBelongToTheVisitedPlaceRatherThanTheCardDrawnFromItsPack() = runBlocking {
        val album = DefaultCardAlbumRepository(store, firstRoll)
        val generation = DefaultProgressRepository(store).getDataGeneration()
        store.transaction {
            val studio = checkNotNull(place("hyundai-motorstudio-goyang"))
            putPlaces(
                listOf(
                    studio.copy(
                        id = "hyundai-motorstudio-seoul",
                        name = "현대 모터스튜디오 서울",
                        district = "서울",
                        regionId = "KR-11",
                        cardPrintId = "hyundai-motorstudio-seoul",
                    ),
                ),
            )
        }
        val request = VisitPackClaimRequest(
            "seoul-drive",
            "hyundai-motorstudio-seoul",
            "현대 모터스튜디오 서울",
            "현대 관련 장소",
            generation,
        )
        val claim = DefaultVisitPackRepository(store, clock).claim(request) as VisitPackClaimResult.Applied
        val regions = DefaultRegionVisitPackSource(store)
        assertEquals(mapOf("KR-11" to 1), regions.regionVisitPackCounts.first())

        val card = album.open(claim.receipt.albumPackId, generation)
        album.reveal(card, generation)
        assertEquals("KR-41", album.observe().first().collection.single().print.mapRegionId)
        assertEquals(mapOf("KR-11" to 1), regions.regionVisitPackCounts.first())
    }

    @Test fun aggregationCountsEveryReceiptAndExcludesMissingOrUnmappedPlaces() = runBlocking {
        DefaultProgressRepository(store).getDataGeneration()
        store.transaction {
            val studio = checkNotNull(place("hyundai-motorstudio-goyang"))
            putPlaces(listOf(studio.copy(active = false), studio.copy(id = "unmapped", regionId = null)))
            repeat(3) { insertVisitPack(receipt("goyang-$it", studio.id)) }
            repeat(2) { insertVisitPack(receipt("busan-$it", "haeundae")) }
            insertVisitPack(receipt("unmapped", "unmapped"))
            insertVisitPack(receipt("missing", "missing-place"))
        }
        assertEquals(
            mapOf("KR-26" to 2, "KR-41" to 3),
            DefaultRegionVisitPackSource(store).regionVisitPackCounts.first(),
        )
        assertEquals(7, real.collectionDao().visitPacks().size)
        assertTrue(real.albumDao().copies().isEmpty())
    }

    @Test fun activeCollectionReceivesNewRegionCountsAndReopenPreservesThem() = runBlocking {
        val regions = DefaultRegionVisitPackSource(store)
        val events = withTimeout(10_000) {
            regions.regionVisitPackCounts.onEach { counts ->
                when (counts.size) {
                    0 -> store.transaction { insertVisitPack(receipt("goyang", "hyundai-motorstudio-goyang")) }
                    1 -> store.transaction { insertVisitPack(receipt("busan", "haeundae")) }
                }
            }.take(3).toList()
        }
        val total = mapOf("KR-41" to 1, "KR-26" to 1)
        assertEquals(listOf(emptyMap(), mapOf("KR-41" to 1), total), events)
        val reopened = CollectionStore(provider, catalog)
        assertEquals(total, DefaultRegionVisitPackSource(reopened).regionVisitPackCounts.first())
    }

    private fun receipt(id: String, placeId: String) =
        VisitPackEntity(id, "journey:$id", placeId, placeId, "관광지", now.toEpochMilli(), 0)
}
