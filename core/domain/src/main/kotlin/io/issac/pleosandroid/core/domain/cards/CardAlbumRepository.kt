package io.issac.pleosandroid.core.domain.cards

import kotlinx.coroutines.flow.Flow

interface CardAlbumRepository {
    fun observe(): Flow<AlbumSnapshot>

    /** The reward provider supplies a stable grant ID. Redelivery returns the original pack. */
    suspend fun receivePack(
        grantId: String,
        packType: PackType,
        source: String,
        place: String? = null,
        journeyId: String? = null,
    ): String
    suspend fun open(packId: String, generation: String? = null): String
    suspend fun reveal(copyId: String, generation: String? = null)
    suspend fun feature(copyId: String, generation: String? = null)
    suspend fun enhance(copyId: String, revision: Long, requestId: String, generation: String? = null): ForgeReceipt
    suspend fun restore(
        copyId: String,
        materialId: String,
        revision: Long,
        requestId: String,
        generation: String? = null,
    ): ForgeReceipt
    suspend fun acknowledge(receiptId: String, generation: String? = null)
}

enum class CardProblem { Missing, InsufficientPoints, Changed, Unavailable, InvalidMaterial }
class CardActionException(val problem: CardProblem) : IllegalStateException(problem.name)
