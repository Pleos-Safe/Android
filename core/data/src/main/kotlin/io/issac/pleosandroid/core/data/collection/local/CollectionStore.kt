package io.issac.pleosandroid.core.data.collection.local

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class CollectionStore @Inject constructor(
    @param:ApplicationContext context: Context,
    private val factory: CollectionDatabaseProvider,
    private val catalog: CatalogSource,
) {
    private val preferences = context.getSharedPreferences("collection_demo", Context.MODE_PRIVATE)
    val demoMode = MutableStateFlow(preferences.getBoolean("enabled", false))
    private val real by lazy { factory.create() }
    private val demo by lazy { factory.create(demo = true) }
    private val initialization = Mutex()
    private val modeChange = Mutex()
    private val initialized = mutableSetOf<CollectionDatabase>()

    private suspend fun ready(db: CollectionDatabase, useDemo: Boolean) {
        initialization.withLock {
            if (db in initialized) return
            val pack = catalog.load()
            db.withTransaction {
                val dao = db.collectionDao()
                if (dao.userState() ==
                    null
                ) {
                    dao.putUserState(UserDataStateEntity(generation = UUID.randomUUID().toString()))
                }
                val existing = dao.catalogState()
                if (existing == null || existing.version <= pack.version) {
                    check(applyCatalog(dao, pack, catalog::hasAsset) !is CatalogResult.Rejected) {
                        "Invalid bundled catalog"
                    }
                }
                if (useDemo) dao.prepareDemoHistory()
                db.prepareCardAlbum(useDemo)
            }
            initialized.add(db)
        }
    }

    suspend fun <T> transaction(block: suspend CollectionDao.() -> T): T = withContext(Dispatchers.IO) {
        val useDemo = demoMode.value
        transactionOn(if (useDemo) demo else real, useDemo, block)
    }

    suspend fun <T> demoTransaction(block: suspend CollectionDao.() -> T): T = withContext(Dispatchers.IO) {
        transactionOn(demo, true, block)
    }

    suspend fun <T> realTransaction(block: suspend CollectionDao.() -> T): T = withContext(Dispatchers.IO) {
        transactionOn(real, false, block)
    }

    private suspend fun <T> transactionOn(
        db: CollectionDatabase,
        useDemo: Boolean,
        block: suspend CollectionDao.() -> T,
    ): T {
        ready(db, useDemo)
        return db.withTransaction { db.collectionDao().block() }
    }

    suspend fun <T> albumTransaction(generation: String? = null, block: suspend AlbumDao.() -> T): T =
        databaseTransaction(generation) { albumDao().block() }

    suspend fun <T> databaseTransaction(generation: String? = null, block: suspend CollectionDatabase.() -> T): T =
        withContext(Dispatchers.IO) {
            val useDemo = demoMode.value
            val db = if (useDemo) demo else real
            ready(db, useDemo)
            db.withTransaction {
                if (generation != null && db.collectionDao().userState()?.generation != generation) {
                    throw CardActionException(CardProblem.Changed)
                }
                db.block()
            }
        }

    suspend fun <T> resetProfile(demoOnly: Boolean = false, block: suspend CollectionDao.() -> T): T =
        withContext(Dispatchers.IO) {
            val useDemo = demoOnly || demoMode.value
            val db = if (useDemo) demo else real
            ready(db, useDemo)
            db.withTransaction {
                val result = db.collectionDao().block()
                if (useDemo) {
                    db.collectionDao().clearMemberships()
                    db.collectionDao().prepareDemoHistory()
                }
                db.resetCardAlbum(useDemo)
                result
            }
        }

    fun <T> observeDatabase(observe: (CollectionDatabase) -> Flow<T>): Flow<T> = observeProfile { db, _ -> observe(db) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observeProfile(observe: (CollectionDatabase, Boolean) -> Flow<T>): Flow<T> =
        demoMode.flatMapLatest { useDemo ->
            flow {
                val db = if (useDemo) demo else real
                ready(db, useDemo)
                emitAll(observe(db, useDemo))
            }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun observe() = observeTransaction { snapshot() }

    fun <T> observeTransaction(block: suspend CollectionDao.() -> T) = observeDatabase { db ->
        db.collectionDao().observeRevision().map { db.withTransaction { db.collectionDao().block() } }
    }

    suspend fun setDemoMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        modeChange.withLock {
            check(preferences.edit().putBoolean("enabled", enabled).commit())
            demoMode.value = enabled
        }
    }
}
