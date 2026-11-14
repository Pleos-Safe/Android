package io.issac.pleosandroid.core.domain.testing

import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardAlbumRepository
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.model.MotionAvailability
import io.issac.pleosandroid.core.domain.model.VehicleGear
import io.issac.pleosandroid.core.domain.model.VehicleMotion
import io.issac.pleosandroid.core.domain.model.VehicleMotionState
import io.issac.pleosandroid.core.domain.model.VehicleState
import io.issac.pleosandroid.core.domain.policy.CarUxPolicy
import io.issac.pleosandroid.core.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class TestCardAlbumRepository : CardAlbumRepository {
    val album = MutableStateFlow(AlbumSnapshot(10000, null, emptyList(), emptyList(), emptyList(), "real"))
    var subscriptions = 0
        private set
    var active = 0
        private set
    var peakActive = 0
        private set
    var failNextLoad = false
    var onOpen: suspend (String, String?) -> String = { _, _ -> error("Unexpected open") }
    var onEnhance: suspend (
        String,
        Long,
        String,
        String?,
    ) -> ForgeReceipt = { _, _, _, _ -> error("Unexpected enhance") }

    override fun observe() = flow {
        subscriptions++
        active++
        peakActive = maxOf(active, peakActive)
        try {
            if (failNextLoad) {
                failNextLoad = false
                error("Failed to read album")
            }
            emitAll(album)
        } finally {
            active--
        }
    }

    override suspend fun open(packId: String, generation: String?) = onOpen(packId, generation)
    override suspend fun enhance(copyId: String, revision: Long, requestId: String, generation: String?) =
        onEnhance(copyId, revision, requestId, generation)
    override suspend fun receivePack(
        grantId: String,
        packType: PackType,
        source: String,
        place: String?,
        journeyId: String?,
    ): String = error("Unexpected pack grant")
    override suspend fun reveal(copyId: String, generation: String?): Unit = error("Unexpected reveal")
    override suspend fun feature(copyId: String, generation: String?): Unit = error("Unexpected featured card")
    override suspend fun restore(
        copyId: String,
        materialId: String,
        revision: Long,
        requestId: String,
        generation: String?,
    ): ForgeReceipt = error("Unexpected restoration")
    override suspend fun acknowledge(receiptId: String, generation: String?): Unit = error("Unexpected acknowledgement")
}

class TestCardVehicle : VehicleRepository {
    override val state = MutableStateFlow(
        VehicleState(
            motion = VehicleMotionState(motion = VehicleMotion.Stopped, availability = MotionAvailability.Available),
            uxPolicy = CarUxPolicy(connected = true, known = true, requiresDistractionOptimization = false),
            gear = VehicleGear.Park,
        ),
    )

    override fun retry() = Unit
}
