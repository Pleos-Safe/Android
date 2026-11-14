package io.issac.pleosandroid.core.data.cards

import io.issac.pleosandroid.core.data.points.openAlbumBalance
import io.issac.pleosandroid.core.database.CollectionDatabase
import io.issac.pleosandroid.core.database.dao.AlbumDao
import io.issac.pleosandroid.core.database.entity.CopyEntity
import io.issac.pleosandroid.core.database.entity.ForgeEntity
import io.issac.pleosandroid.core.database.entity.PackEntity
import io.issac.pleosandroid.core.database.entity.PrintEntity
import io.issac.pleosandroid.core.database.entity.VisitPackEntity
import io.issac.pleosandroid.core.database.entity.WalletEntity
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CardTravelMemory
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.core.domain.collection.model.visitAlbumPackId
import java.time.Instant
import java.time.LocalDate

internal fun CardPrint.row() = PrintEntity(
    id, title, packType?.name, group.name, baseGrade.name, artwork, variantArtwork.orEmpty(),
    place, serial, mapRegionId,
)
internal fun PrintEntity.model() = CardPrint(
    id, title,
    packType?.let(
        PackType::valueOf,
    ),
    PrintGroup.valueOf(pool), CardGrade.valueOf(baseGrade), art,
    variantArt.ifEmpty {
        null
    },
    place, serial, mapRegionId,
)
internal fun PackEntity.model() =
    ReceivedPack(id, packType?.let(PackType::valueOf), source, Instant.ofEpochMilli(receivedAt), place, journeyId)
internal fun CopyEntity.model(print: CardPrint, pack: ReceivedPack, memories: List<CardTravelMemory> = emptyList()) =
    CardCopy(
        id = id,
        print = print,
        variant = variant,
        level = level,
        highest = highest,
        status = CopyStatus.valueOf(status),
        revision = revision,
        pack = pack,
        acquiredAt = Instant.ofEpochMilli(acquiredAt),
        seen = seenAt != null,
        travelMemories = memories,
    )
internal fun ForgeEntity.model() = ForgeReceipt(
    id, copyId, beforeLevel, afterLevel,
    ForgeOutcome.valueOf(outcome),
    cost, Instant.ofEpochMilli(at), shown, materialId,
)

internal suspend fun AlbumDao.snapshot(balanceHalfPoints: Long, generation: String): AlbumSnapshot {
    val wallet = checkNotNull(wallet())
    val packs = packs().map { it.model() }.associateBy { it.id }
    val prints = prints().map { it.model() }.associateBy { it.id }
    val memories = importedMemories().groupBy(
        { it.copyId },
        { CardTravelMemory(LocalDate.parse(it.localDate), it.journeyId) },
    )
    return AlbumSnapshot(
        balanceHalfPoints,
        wallet.featuredId,
        packs.values.toList(),
        copies().map {
            it.model(prints.getValue(it.printId), packs.getValue(it.packId), memories[it.id].orEmpty())
        },
        actions().map { it.model() },
        generation,
    )
}

/** Called inside the profile database transaction, including first launch after upgrading v3. */
internal suspend fun CollectionDatabase.prepareCardAlbum() {
    val album = albumDao()
    album.removeImportedLevelNotices()
    album.savePrints(regionalPrints.map { it.row() })
    val previous = collectionDao()
    for (visit in previous.visitPacks()) {
        val type = previous.place(visit.placeId)?.rewardPack ?: continue
        album.grantVisitPack(visit, type)
    }
    if (album.wallet() != null) return
    val now = System.currentTimeMillis()
    album.grant(PackEntity("welcome:seoul", PackType.Seoul.name, "첫 여행 선물", now))
    val definitions = previous.definitions().associateBy { it.id }
    val places = previous.places().associateBy { it.id }
    val owned = previous.ownedCards()
    for (old in owned) {
        val def = definitions.getValue(old.cardId)
        val packType = when (places[def.placeId]?.regionId) {
            "KR-11" -> PackType.Seoul
            "KR-26" -> PackType.Busan
            "KR-49", "KR-50" -> PackType.Jeju
            else -> null
        }
        val place = places[def.placeId]?.name ?: def.context
        val print =
            regionalPrints.find { it.packType == packType && it.id.substringAfter('-') == def.placeId } ?: continue
        val pack = PackEntity("legacy:" + old.ownershipId, packType?.name, "이전 컬렉션", old.acquiredAt, place)
        album.grant(pack)
        val level = (old.earnedLevel - 1) * 5
        album.add(
            CopyEntity(
                old.ownershipId, pack.id, print.id, false, level, level, CopyStatus.Owned.name, 0,
                old.acquiredAt, old.revealedLevel?.let { old.acquiredAt }, old.cardId,
            ),
        )
    }
    previous.openAlbumBalance(2_000L, Instant.ofEpochMilli(now))
    val representative = previous.display()?.cardId
    album.save(
        WalletEntity(
            featuredId = owned.firstOrNull { it.cardId == representative && it.revealedLevel != null }?.ownershipId
                ?.takeIf { album.copy(it) != null },
        ),
    )
}

internal suspend fun AlbumDao.grantVisitPack(visit: VisitPackEntity, type: String) {
    grant(PackEntity(visitAlbumPackId(visit.id), type, "방문 보상", visit.claimedAt, visit.placeTitle, visit.journeyId))
}

internal suspend fun CollectionDatabase.resetCardAlbum() {
    with(albumDao()) {
        clearActions()
        clearCopies()
        clearPacks()
        clearWallet()
    }
    prepareCardAlbum()
}
