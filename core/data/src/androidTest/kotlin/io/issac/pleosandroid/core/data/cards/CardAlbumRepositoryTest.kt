package io.issac.pleosandroid.core.data.cards

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.issac.pleosandroid.core.data.collection.local.CatalogLoader
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.database.entity.CollectionDisplayEntity
import io.issac.pleosandroid.core.database.entity.OwnedCardEntity
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardAlbumRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: CollectionDatabase
    private lateinit var repository: DefaultCardAlbumRepository
    private val sequence = ArrayDeque<Int>()
    private var roll = 50
    private val random = object : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextInt(until: Int) = if (until == 100) sequence.removeFirstOrNull() ?: roll else 0
    }

    private val catalog = object : CatalogSource {
        override fun load() = CatalogLoader(context).load()
        override fun hasAsset(key: String) = true
    }

    private fun connect() {
        val provider = object : CollectionDatabaseProvider {
            override fun create(demo: Boolean) = db
        }
        repository = DefaultCardAlbumRepository(CollectionStore(context, provider, catalog), random)
    }

    private fun oldDefinition(place: String) = io.issac.pleosandroid.core.database.entity.CardDefinitionEntity(
        "place-$place-001", "Place", place, place, "", "", "", "art", "gloss", "engraving", "Standard", 1, true,
    )

    @Before fun setup() {
        context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE).edit().remove("enabled").commit()
        db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        connect()
    }

    @After fun close() = db.close()

    private suspend fun snapshot() = repository.observe().first()

    private suspend fun draw(id: String, region: PackType = PackType.Seoul): String {
        val pack = repository.receivePack(id, region, "여행", "주차장", "journey:1")
        return repository.open(pack).also { repository.reveal(it) }
    }

    private suspend fun rejected(reason: CardProblem, action: suspend () -> Unit) {
        assertEquals(reason, (runCatching { action() }.exceptionOrNull() as? CardActionException)?.problem)
    }

    @Test fun concurrentGrantAndOpenResumeTheSameCopyAndKeepDuplicatesSeparate() = runBlocking<Unit> {
        assertEquals(2000L, snapshot().balanceHalfPoints)
        val welcome = snapshot().inbox.single()
        assertEquals(PackType.Seoul, welcome.packType)
        val ids = (1..8).map { async { repository.open(welcome.id) } }.awaitAll()
        assertEquals(1, ids.distinct().size)
        assertTrue(snapshot().collection.isEmpty())
        connect()
        assertEquals(ids.first(), repository.open(welcome.id))
        repository.reveal(ids.first())
        val second = draw("arrival-1")
        assertNotEquals(second, ids.first())
        assertEquals(
            "reward:arrival-1",
            repository.receivePack("arrival-1", PackType.Seoul, "여행", "주차장", "journey:1"),
        )
        val copies = snapshot().collection
        assertEquals(2, copies.size)
        assertEquals(1, copies.distinctBy { it.print.id }.size)
        assertTrue(snapshot().inbox.isEmpty())
        assertEquals(2, snapshot().packs.size)
        assertEquals("주차장", copies.single { it.id == second }.pack.place)
    }

    @Test fun chargeAndResultAreAtomicAcrossRetriesRecreationAndInsufficientFunds() = runBlocking<Unit> {
        val id = draw("a")
        roll = 90
        val receipts = (1..4).map { async { repository.enhance(id, 0, "first") } }.awaitAll()
        assertEquals(1, receipts.distinct().size)
        assertEquals(ForgeOutcome.GreatSuccess, receipts.first().outcome)
        connect()
        assertEquals(2, snapshot().collection.single().level)
        assertEquals(1800L, snapshot().balanceHalfPoints)
        assertFalse(snapshot().pending.getValue(id).acknowledged)
        rejected(CardProblem.Changed) { repository.enhance(id, 1, "parallel") }
        repository.acknowledge("first")
        rejected(CardProblem.Changed) { repository.enhance(id, 0, "stale") }
        val ledger = db.collectionDao()
        ledger.insertPointTransaction(
            ledger.pointTransactions().first().copy(operationId = "prior-spend", deltaHalfPoints = -1799),
        )
        rejected(CardProblem.InsufficientPoints) { repository.enhance(id, 1, "too-expensive") }
        assertEquals(1L, snapshot().balanceHalfPoints)
        assertEquals(1, snapshot().history.size)
    }

    @Test fun existingTraceKeepsIdentityAndOnlyOneEligibleMaterialIsConsumedOnRestoration() = runBlocking<Unit> {
        roll = 99
        val original = draw("one")
        val material = draw("two")
        sequence.addAll(listOf(99, 4))
        val variant = draw("variant")
        roll = 99
        val differentPrint = draw("wrong", PackType.Jeju)
        val dao = db.albumDao()
        val before = dao.copy(original)!!
        dao.save(before.copy(level = 7, highest = 16, status = CopyStatus.Trace.name, revision = 1))
        dao.log(
            io.issac.pleosandroid.core.database.entity.ForgeEntity(
                "destroy", original, 12, 7, ForgeOutcome.Destroyed.name, 0, before.acquiredAt, false, null,
            ),
        )
        connect()
        val destroyed = snapshot().pending.getValue(original)
        assertEquals(ForgeOutcome.Destroyed, destroyed.outcome)
        assertEquals(7, destroyed.after)
        assertNull(snapshot().featuredId)
        assertEquals(CopyStatus.Trace, snapshot().byPack.getValue(before.packId).status)
        rejected(CardProblem.Changed) { repository.restore(original, material, 1, "too-early") }
        repository.acknowledge("destroy")
        rejected(CardProblem.InvalidMaterial) { repository.restore(original, variant, 1, "variant") }
        rejected(CardProblem.InvalidMaterial) { repository.restore(original, differentPrint, 1, "other") }
        val balance = snapshot().balanceHalfPoints
        repository.feature(material)
        val restored = repository.restore(original, material, 1, "restore")
        assertEquals(restored, repository.restore(original, material, 1, "restore"))
        assertEquals(balance, snapshot().balanceHalfPoints)
        assertEquals(16, snapshot().collection.single { it.id == original }.highest)
        assertEquals(CopyStatus.Spent.name, dao.copy(material)!!.status)
        assertEquals(before.acquiredAt, dao.copy(original)!!.acquiredAt)
        assertEquals(material, restored.materialId)
        assertNull(snapshot().featuredId)
        assertTrue(snapshot().collection.none { it.id == material })
        assertFalse(snapshot().inbox.any { it.id == dao.copy(material)!!.packId })
    }

    @Test fun specialtyVariantIsEpicAndChargedAtTheEpicPrice() = runBlocking<Unit> {
        val sequence = ArrayDeque(listOf(96, 4, 0))
        val controlled = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextInt(until: Int) = if (until == 100) sequence.removeFirst() else 0
        }
        repository = DefaultCardAlbumRepository(
            CollectionStore(
                context,
                object : CollectionDatabaseProvider {
                    override fun create(demo: Boolean) = db
                },
                catalog,
            ),
            controlled,
        )
        val id = draw("epic", PackType.Busan)
        assertEquals(CardGrade.Epic, snapshot().collection.single().grade)
        val dao = db.albumDao()
        dao.save(dao.copy(id)!!.copy(level = 5, highest = 5))
        val receipt = repository.enhance(id, 0, "cost")
        assertEquals(CardRules.cost(CardGrade.Epic, 5), receipt.costHalfPoints)
        assertEquals(1325L, snapshot().balanceHalfPoints)
    }

    @Test fun existingOwnershipIsImportedOnceWithItsOriginalDatesRevealAndRepresentative() = runBlocking<Unit> {
        snapshot()
        val old = db.collectionDao()
        old.putDefinitions(listOf(oldDefinition("haeundae"), oldDefinition("gwangalli")))
        old.putOwned(OwnedCardEntity("place-haeundae-001", "known-owner", 3, 2, 1000, 1))
        old.putOwned(OwnedCardEntity("place-gwangalli-001", "sealed-owner", 1, null, 2000, 1))
        old.insertVisit(
            io.issac.pleosandroid.core.database.entity.VisitEntity(
                "historic-visit", "haeundae", null, 100, 500, 1000, "Asia/Seoul", 0.0, 1.0, 1,
            ),
        )
        old.putMemories(
            listOf(
                io.issac.pleosandroid.core.database.entity.CardMemoryEntity(
                    "historic-memory",
                    "place-haeundae-001",
                    "historic-visit",
                    null,
                    1000,
                    "2026-01-01",
                ),
            ),
        )
        old.putDisplay(CollectionDisplayEntity(cardId = "place-haeundae-001"))
        // A migrated v3 installation has the legacy rows, but no album wallet yet.
        db.albumDao().clearWallet()
        connect()
        val album = snapshot()
        assertEquals("known-owner", album.featuredId)
        assertEquals(1000L, album.collection.single().acquiredAt.toEpochMilli())
        assertEquals(10, album.collection.single().level)
        assertEquals("2026-01-01", album.collection.single().travelMemories.single().date.toString())
        db.albumDao().log(
            io.issac.pleosandroid.core.database.entity.ForgeEntity(
                "legacy:advance", "known-owner", 5, 10, "LegacyAdvance", 0, 1000, false, null,
            ),
        )
        connect()
        assertTrue(snapshot().pending.isEmpty())
        assertTrue(album.pending.isEmpty())
        assertEquals("busan-haeundae", album.collection.single().print.id)
        assertFalse(album.byPack.getValue("legacy:sealed-owner").seen)
        assertEquals("sealed-owner", repository.open("legacy:sealed-owner"))
        repository.reveal("sealed-owner")
        connect()
        assertEquals(2, snapshot().collection.size)
        assertEquals(1, snapshot().packs.count { it.id == "welcome:seoul" })
    }

    @Test fun unrecognizedOldPrintNeverReentersTheAlbumOrBecomesItsRepresentative() = runBlocking<Unit> {
        snapshot()
        val old = db.collectionDao()
        val retired = "place-igidae-001"
        old.putDefinitions(listOf(oldDefinition("igidae")))
        old.putOwned(OwnedCardEntity(retired, "retired", 2, 2, 1000, 1))
        old.putDisplay(CollectionDisplayEntity(cardId = retired))
        db.albumDao().clearWallet()
        connect()
        val album = snapshot()
        assertNull(album.featuredId)
        assertTrue(album.copies.none { it.id == "retired" })
        assertTrue(album.packs.none { it.id == "legacy:retired" })
        assertEquals(regionalPrints.map { it.id }.toSet(), db.albumDao().prints().map { it.id }.toSet())
    }
}
