package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.database.entity.UserDataStateEntity
import io.issac.pleosandroid.core.domain.collection.repository.CollectionDemoRepository
import java.util.UUID
import javax.inject.Inject

internal class DefaultCollectionDemoRepository @Inject constructor(private val store: CollectionStore) :
    CollectionDemoRepository {
    override val available = true
    override val enabled = store.demoMode

    override suspend fun setEnabled(enabled: Boolean) {
        if (enabled) store.demoTransaction { Unit }
        store.setDemoMode(enabled)
    }

    override suspend fun resetDemo() {
        store.resetProfile(demoOnly = true) {
            clearOwned()
            clearVisits()
            clearJourneys()
            clearOperations()
            clearDrivingDraft()
            clearPointTransactions()
            clearVisitPacks()
            putUserState(
                UserDataStateEntity(
                    generation = UUID.randomUUID().toString(),
                    revision = checkNotNull(userState()).revision + 1,
                ),
            )
        }
    }
}
