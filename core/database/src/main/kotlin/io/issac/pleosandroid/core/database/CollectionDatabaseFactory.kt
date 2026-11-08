package io.issac.pleosandroid.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import io.issac.pleosandroid.core.database.security.DatabaseKeyStore
import javax.inject.Inject
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

interface CollectionDatabaseProvider {
    fun create(demo: Boolean = false): CollectionDatabase
}

@Singleton
class CollectionDatabaseFactory @Inject constructor(@param:ApplicationContext private val context: Context) :
    CollectionDatabaseProvider {
    private val keys = DatabaseKeyStore(context)

    override fun create(demo: Boolean): CollectionDatabase {
        val name = if (demo) "collection-demo.db" else "collection.db"
        // Room calls create on the DB executor; Keystore and disk access stay off the UI thread.
        val factory = SupportSQLiteOpenHelper.Factory { configuration ->
            System.loadLibrary("sqlcipher")
            SupportOpenHelperFactory(keys.getOrCreate(name)).create(configuration)
        }
        return Room.databaseBuilder(context, CollectionDatabase::class.java, name)
            .openHelperFactory(factory)
            .addCallback(CollectionDatabase.constraints)
            .addMigrations(
                CollectionDatabase.migration1To2,
                CollectionDatabase.migration2To3,
                CollectionDatabase.migration3To4,
            )
            .build()
    }
}
