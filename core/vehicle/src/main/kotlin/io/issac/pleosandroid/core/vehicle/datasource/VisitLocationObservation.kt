package io.issac.pleosandroid.core.vehicle.datasource

import io.issac.pleosandroid.core.domain.collection.repository.VisitLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive

/** Permission can change while the recording service keeps the same visit collector alive. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeVisitLocations(
    hasPermission: () -> Boolean,
    updates: () -> Flow<VisitLocation?>,
): Flow<VisitLocation?> = flow {
    while (currentCoroutineContext().isActive) {
        emit(hasPermission())
        delay(1_000)
    }
}.distinctUntilChanged().flatMapLatest { allowed ->
    if (allowed) updates() else flowOf(null)
}.transformLatest { fix ->
    emit(fix)
    if (fix != null) {
        delay(15_000)
        emit(null)
    }
}
