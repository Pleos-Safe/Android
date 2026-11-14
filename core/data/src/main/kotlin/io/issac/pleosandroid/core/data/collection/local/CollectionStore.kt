package io.issac.pleosandroid.core.data.collection.local

import androidx.room.withTransaction
import io.issac.pleosandroid.core.data.cards.prepareCardAlbum
import io.issac.pleosandroid.core.data.cards.resetCardAlbum
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.database.dao.AlbumDao
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.domain.cards.CardActionException
import io.issac.pleosandroid.core.domain.cards.CardProblem
import io.issac.pleosandroid.core.domain.collection.model.CatalogResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class CollectionStore @Inject constructor(
    private val factory: CollectionDatabaseProvider,
    private val catalog: CatalogSource,
) {
    private val db by lazy { factory.create() }
    private val initialization = Mutex()
    private var initialized = false

    private suspend fun ready() = initialization.withLock {
        if (initialized) return@withLock
        val pack = catalog.load()
        db.withTransaction {
            val dao = db.collectionDao()
            if (dao.userState() == null) {
                dao.putUserState(UserDataStateEntity(generation = UUID.randomUUID().toString()))
            }
            val existing = dao.catalogState()
            if (existing == null || existing.version <= pack.version) {
                check(applyCatalog(dao, pack, catalog::hasAsset) !is CatalogResult.Rejected) {
                    "Invalid bundled catalog"
                }
            }
            db.prepareCardAlbum()
        }
        initialized = true
    }

    suspend fun <T> transaction(block: suspend CollectionDao.() -> T): T =
        databaseTransaction { collectionDao().block() }

    suspend fun <T> albumTransaction(generation: String? = null, block: suspend AlbumDao.() -> T): T =
        databaseTransaction(generation) { albumDao().block() }

    suspend fun <T> databaseTransaction(generation: String? = null, block: suspend CollectionDatabase.() -> T): T =
        withContext(Dispatchers.IO) {
            ready()
            db.withTransaction {
                if (generation != null && db.collectionDao().userState()?.generation != generation) {
                    throw CardActionException(CardProblem.Changed)
                }
                db.block()
            }
        }

    suspend fun <T> resetProfile(block: suspend CollectionDao.() -> T): T = databaseTransaction {
        val result = collectionDao().block()
        resetCardAlbum()
        result
    }

    fun <T> observeDatabase(observe: (CollectionDatabase) -> Flow<T>): Flow<T> = flow {
        ready()
        emitAll(observe(db))
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun observe() = observeTransaction { snapshot() }

    fun <T> observeTransaction(block: suspend CollectionDao.() -> T) = observeDatabase { db ->
        db.collectionDao().observeRevision().map { db.withTransaction { db.collectionDao().block() } }
    }
}
