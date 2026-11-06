package io.issac.pleosandroid.core.domain.collection.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class VisitLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val elapsedNanos: Long,
)

/** Locations with measured horizontal accuracy, not navigation-arrival hints. */
interface VisitLocationSource {
    val locations: Flow<VisitLocation?>
}

enum class VisitRecordingStatus { Waiting, Observing, Saved, StorageError }

interface VisitRecorder {
    val status: StateFlow<VisitRecordingStatus>
    suspend fun monitor()
}
