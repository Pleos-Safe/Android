package io.issac.pleosandroid.core.data.collection.repository

import io.issac.pleosandroid.core.data.collection.local.CardAwardWriter
import io.issac.pleosandroid.core.data.collection.local.CatalogLoader
import io.issac.pleosandroid.core.data.collection.local.CatalogSource
import io.issac.pleosandroid.core.data.collection.local.CollectionStore
import io.issac.pleosandroid.core.data.collection.local.applyCatalog
import io.issac.pleosandroid.core.data.collection.local.decodeReceipt
import io.issac.pleosandroid.core.data.collection.local.encode
import io.issac.pleosandroid.core.data.collection.local.fingerprint
import io.issac.pleosandroid.core.database.entity.ProgressOperationEntity
import io.issac.pleosandroid.core.domain.collection.model.CatalogPack
import io.issac.pleosandroid.core.domain.collection.model.CatalogResult
import io.issac.pleosandroid.core.domain.collection.model.ProgressRejection
import io.issac.pleosandroid.core.domain.collection.model.RecordProgressResult
import io.issac.pleosandroid.core.domain.collection.model.ReevaluateRulesRequest
import io.issac.pleosandroid.core.domain.collection.repository.CatalogRepository
import javax.inject.Inject

internal class DefaultCatalogRepository @Inject constructor(
    private val store: CollectionStore,
    private val loader: CatalogSource,
    private val awards: CardAwardWriter,
) : CatalogRepository {
    override suspend fun applyCatalog(pack: CatalogPack): CatalogResult = store.transaction {
        applyCatalog(this, pack, loader::hasAsset)
    }

    override suspend fun reevaluateRules(request: ReevaluateRulesRequest): RecordProgressResult = storageResult(
        RecordProgressResult.StorageFailure,
    ) {
        store.transaction {
            if (userState()?.generation !=
                request.dataGeneration
            ) {
                return@transaction RecordProgressResult.Rejected(ProgressRejection.StaleGeneration)
            }
            if (request.operationId.isBlank() || request.cardIds.isEmpty() ||
                request.cardIds.any { id -> definitions().none { it.id == id } }
            ) {
                return@transaction RecordProgressResult.Rejected(ProgressRejection.InvalidInput)
            }
            val hash = fingerprint(request.copy(cardIds = request.cardIds.toSortedSet()).toString())
            operation(request.operationId)?.let { old ->
                return@transaction if (old.fingerprint == hash && !old.invalidated) {
                    RecordProgressResult.AlreadyProcessed(decodeReceipt(old.id, old.receipt))
                } else {
                    RecordProgressResult.Rejected(ProgressRejection.ConflictingOperation)
                }
            }
            val receipt = awards.reconcile(this, request.operationId, cardIds = request.cardIds)
            insertOperation(
                ProgressOperationEntity(request.operationId, "rules", request.operationId, 1, hash, receipt.encode()),
            )
            RecordProgressResult.Applied(receipt)
        }
    }
}
