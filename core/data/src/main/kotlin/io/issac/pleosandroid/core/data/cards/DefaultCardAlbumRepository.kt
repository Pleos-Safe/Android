package io.issac.pleosandroid.core.data.cards

import androidx.room.withTransaction
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.points.debitEnhancement
import io.issac.pleosandroid.core.database.dao.AlbumDao
import io.issac.pleosandroid.core.database.entity.CopyEntity
import io.issac.pleosandroid.core.database.entity.ForgeEntity
import io.issac.pleosandroid.core.database.entity.PackEntity
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardAlbumRepository
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.grade
import io.issac.pleosandroid.core.domain.points.PointSpendResult
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.map

internal class DefaultCardAlbumRepository(private val store: CollectionStore, private val random: Random) :
    CardAlbumRepository {
    @Inject constructor(store: CollectionStore) : this(store, Random.Default)

    override fun observe() = store.observeDatabase { db ->
        val tables =
            arrayOf(
                "album_pack",
                "album_copy",
                "album_wallet",
                "album_action",
                "album_print",
                "point_transaction",
                "user_data_state",
                "card_memory",
                "visit",
            )
        db.invalidationTracker.createFlow(*tables).map {
            db.withTransaction {
                val profile = db.collectionDao()
                db.albumDao().snapshot(profile.balanceHalfPoints(), checkNotNull(profile.userState()).generation)
            }
        }
    }

    override suspend fun receivePack(
        grantId: String,
        packType: PackType,
        source: String,
        place: String?,
        journeyId: String?,
    ) = store.albumTransaction {
        require(grantId.isNotBlank() && source.isNotBlank())
        val id = "reward:$grantId"
        val pack = PackEntity(id, packType.name, source, System.currentTimeMillis(), place, journeyId)
        if (grant(pack) == -1L) {
            val original = checkNotNull(pack(id))
            require(original.copy(receivedAt = pack.receivedAt) == pack) {
                "The grant already belongs to a different reward"
            }
        }
        id
    }

    override suspend fun open(packId: String, generation: String?): String = store.albumTransaction(generation) {
        draw(packId)?.let { return@albumTransaction it.id }
        val pack = pack(packId) ?: fail(CardProblem.Missing)
        val (print, variant) = CardRules.draw(PackType.valueOf(checkNotNull(pack.packType)), regionalPrints, random)
        val id = UUID.randomUUID().toString()
        val copy = CopyEntity(
            id, packId, print.id, variant, 0, 0, CopyStatus.Owned.name, 0,
            System.currentTimeMillis(), null,
        )
        add(copy)
        id
    }

    override suspend fun reveal(copyId: String, generation: String?) = store.albumTransaction(generation) {
        val card = copy(copyId) ?: fail(CardProblem.Missing)
        if (card.seenAt == null) save(card.copy(seenAt = System.currentTimeMillis()))
    }

    override suspend fun feature(copyId: String, generation: String?) = store.albumTransaction(generation) {
        val card = copy(copyId) ?: fail(CardProblem.Missing)
        if (card.status != CopyStatus.Owned.name || card.seenAt == null) fail(CardProblem.Unavailable)
        save(checkNotNull(wallet()).copy(featuredId = copyId))
    }

    override suspend fun enhance(copyId: String, revision: Long, requestId: String, generation: String?) =
        store.databaseTransaction(generation) {
            val ledger = collectionDao()
            with(albumDao()) {
                previousAttempt(copyId, requestId, null)?.let { return@databaseTransaction it.model() }
                val card = currentCopy(copyId, revision, CopyStatus.Owned)
                val print = print(card.printId).model()
                val grade = print.grade(card.variant)
                val price = CardRules.cost(grade, card.level)
                val wallet = checkNotNull(wallet())
                val at = Instant.now()
                when (ledger.debitEnhancement(requestId, price, at)) {
                    is PointSpendResult.Applied -> Unit
                    PointSpendResult.InsufficientBalance -> fail(CardProblem.InsufficientPoints)
                    else -> fail(CardProblem.Changed)
                }
                val outcome = CardRules.odds(card.level).result(random.nextInt(100))
                val after = CardRules.nextLevel(card.level, outcome)
                val trace = outcome == ForgeOutcome.Destroyed
                val updated = card.copy(
                    level = after,
                    highest = maxOf(after, card.highest),
                    revision = revision + 1,
                    status = if (trace) CopyStatus.Trace.name else CopyStatus.Owned.name,
                )
                save(updated)
                val featured = wallet.featuredId.takeUnless { trace && it == copyId }
                if (featured != wallet.featuredId) save(wallet.copy(featuredId = featured))
                val result = ForgeEntity(
                    requestId, copyId, card.level, after, outcome.name, price, at.toEpochMilli(), false, null,
                )
                log(result)
                result.model()
            }
        }

    override suspend fun restore(
        copyId: String,
        materialId: String,
        revision: Long,
        requestId: String,
        generation: String?,
    ) = store.albumTransaction(generation) {
        previousAttempt(copyId, requestId, materialId)?.let { return@albumTransaction it.model() }
        val target = currentCopy(copyId, revision, CopyStatus.Trace)
        val material = copy(materialId) ?: fail(CardProblem.InvalidMaterial)
        if (pending(materialId) != null || target.printId != material.printId) fail(CardProblem.InvalidMaterial)
        val definition = print(target.printId).model()
        val trace = target.model(definition, checkNotNull(pack(target.packId)).model())
        val candidate = material.model(definition, checkNotNull(pack(material.packId)).model())
        if (!trace.canRestoreWith(candidate)) fail(CardProblem.InvalidMaterial)
        save(material.copy(status = CopyStatus.Spent.name, revision = material.revision + 1))
        save(target.copy(status = CopyStatus.Owned.name, revision = revision + 1))
        val wallet = checkNotNull(wallet())
        if (wallet.featuredId == materialId) save(wallet.copy(featuredId = null))
        val result = ForgeEntity(
            requestId, copyId, target.level, target.level, ForgeOutcome.Restored.name,
            0, System.currentTimeMillis(), false, materialId,
        )
        log(result)
        result.model()
    }

    override suspend fun acknowledge(receiptId: String, generation: String?) = store.albumTransaction(generation) {
        acknowledge(receiptId)
    }

    private suspend fun AlbumDao.previousAttempt(copyId: String, requestId: String, material: String?): ForgeEntity? {
        require(requestId.isNotBlank())
        val saved = action(requestId) ?: return null
        if (saved.copyId != copyId || saved.materialId != material) fail(CardProblem.Changed)
        return saved
    }

    private suspend fun AlbumDao.currentCopy(id: String, revision: Long, status: CopyStatus): CopyEntity {
        val card = copy(id) ?: fail(CardProblem.Missing)
        if (card.revision != revision || pending(id) != null) fail(CardProblem.Changed)
        if (card.status != status.name || card.seenAt == null) fail(CardProblem.Unavailable)
        return card
    }

    private fun fail(reason: CardProblem): Nothing = throw CardActionException(reason)
}
