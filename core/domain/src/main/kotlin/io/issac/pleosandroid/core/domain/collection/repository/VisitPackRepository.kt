package io.issac.pleosandroid.core.domain.collection.repository

import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimRequest
import io.issac.pleosandroid.core.domain.collection.model.VisitPackClaimResult
import io.issac.pleosandroid.core.domain.collection.model.VisitPackReceipt
import kotlinx.coroutines.flow.Flow

interface VisitPackRepository {
    fun observeInbox(): Flow<List<VisitPackReceipt>>

    /** Records an eligible arrival after the caller verifies current vehicle and visit evidence. */
    suspend fun claim(request: VisitPackClaimRequest): VisitPackClaimResult
}
