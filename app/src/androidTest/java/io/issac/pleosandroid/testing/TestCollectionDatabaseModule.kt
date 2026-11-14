package io.issac.pleosandroid.testing

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.CollectionDatabaseModule
import io.issac.pleosandroid.core.database.CollectionDatabaseProvider
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CollectionDatabaseModule::class])
object TestCollectionDatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): CollectionDatabase =
        Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java)
            .addCallback(CollectionDatabase.constraints)
            .build()

    @Provides
    fun provider(database: CollectionDatabase): CollectionDatabaseProvider = object : CollectionDatabaseProvider {
        override fun create(demo: Boolean): CollectionDatabase = database
    }
}
