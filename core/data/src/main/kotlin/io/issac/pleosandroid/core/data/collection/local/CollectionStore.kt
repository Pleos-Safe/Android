package io.issac.pleosandroid.core.data.collection.local

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.data.BuildConfig
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseFactory
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import io.issac.pleosandroid.core.database.dao.CollectionDao
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.domain.collection.model.CatalogResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val demoMode = MutableStateFlow(BuildConfig.DEBUG && preferences.getBoolean("enabled", false))
    private val real by lazy { factory.create() }
    private val demo by lazy { factory.create(demo = true) }
    private val initialization = Mutex()
    private val initialized = mutableSetOf<CollectionDatabase>()

    private suspend fun ready(db: CollectionDatabase) {
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
            }
            initialized.add(db)
        }
    }

    suspend fun <T> transaction(block: suspend CollectionDao.() -> T): T = withContext(Dispatchers.IO) {
        transactionOn(if (demoMode.value) demo else real, block)
    }

    suspend fun <T> demoTransaction(block: suspend CollectionDao.() -> T): T = withContext(Dispatchers.IO) {
        check(BuildConfig.DEBUG)
        transactionOn(demo, block)
    }

    suspend fun <T> realTransaction(block: suspend CollectionDao.() -> T): T = withContext(Dispatchers.IO) {
        transactionOn(real, block)
    }

    private suspend fun <T> transactionOn(db: CollectionDatabase, block: suspend CollectionDao.() -> T): T {
        ready(db)
        return db.withTransaction { db.collectionDao().block() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe() = demoMode.flatMapLatest { useDemo ->
        flow {
            val db = if (useDemo) demo else real
            ready(db)
            emitAll(
                db.collectionDao().observeRevision().map {
                    db.withTransaction { db.collectionDao().snapshot() }
                },
            )
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun setDemoMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        require(!enabled || BuildConfig.DEBUG)
        check(preferences.edit().putBoolean("enabled", enabled).commit())
        demoMode.value = enabled
    }
}
