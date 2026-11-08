package io.issac.pleosandroid.core.domain.cards

import java.time.Instant

enum class PackType(val title: String, val packTitle: String) {
    Seoul("서울", "서울 컬렉션 팩"),
    Busan("부산", "부산 컬렉션 팩"),
    Jeju("제주", "제주 컬렉션 팩"),
    Hyundai("현대", "현대 팩"),
}
enum class CardGrade { Common, Rare, Epic, H, HH }
enum class PrintGroup(val title: String, val grade: CardGrade) {
    Beach("해수욕장", CardGrade.Common),
    Landmark("랜드마크", CardGrade.Common),
    Specialty("지역 특색", CardGrade.Rare),
    Studio("현대 스튜디오", CardGrade.HH),
    Epit("E-pit", CardGrade.H),
}
enum class CopyStatus { Owned, Trace, Spent }
enum class ForgeOutcome { Success, GreatSuccess, Hold, Drop, Destroyed, Restored }

data class CardPrint(
    val id: String,
    val title: String,
    val packType: PackType?,
    val group: PrintGroup,
    val baseGrade: CardGrade,
    val artwork: String,
    val variantArtwork: String?,
    val place: String,
    val serial: String,
    val mapRegionId: String? = null,
)

fun CardPrint.grade(variant: Boolean): CardGrade = if (variant) baseGrade.variantGrade() else baseGrade

internal fun CardGrade.variantGrade(): CardGrade = when (this) {
    CardGrade.Common -> CardGrade.Rare
    CardGrade.Rare -> CardGrade.Epic
    else -> this
}

data class ReceivedPack(
    val id: String,
    val packType: PackType?,
    val source: String,
    val receivedAt: Instant,
    val place: String? = null,
    val journeyId: String? = null,
)
data class CardTravelMemory(val date: java.time.LocalDate, val journeyId: String?)

data class CardCopy(
    val id: String,
    val print: CardPrint,
    val variant: Boolean,
    val level: Int,
    val highest: Int,
    val status: CopyStatus,
    val revision: Long,
    val pack: ReceivedPack,
    val acquiredAt: Instant,
    val seen: Boolean,
    val travelMemories: List<CardTravelMemory> = emptyList(),
) {
    val grade: CardGrade get() = print.grade(variant)

    fun canRestoreWith(other: CardCopy): Boolean = status == CopyStatus.Trace && other.id != id && other.seen &&
        other.status == CopyStatus.Owned && other.level == 0 && other.print.id == print.id && other.variant == variant
}

data class ForgeReceipt(
    val id: String,
    val copyId: String,
    val before: Int,
    val after: Int,
    val outcome: ForgeOutcome,
    val costHalfPoints: Long,
    val at: Instant,
    val acknowledged: Boolean,
    val materialId: String?,
)

data class AlbumSnapshot(
    val balanceHalfPoints: Long,
    val featuredId: String?,
    val packs: List<ReceivedPack>,
    val copies: List<CardCopy>,
    val history: List<ForgeReceipt>,
    val generation: String? = null,
    val demo: Boolean = false,
) {
    val pending = history.filterNot { it.acknowledged }.associateBy { it.copyId }
    fun materialsFor(trace: CardCopy) = collection.filter { it.id !in pending && trace.canRestoreWith(it) }
    val byPack = copies.associateBy { it.pack.id }
    val inbox = packs.filter { byPack[it.id]?.seen != true }
    val collection = copies.filter { it.seen && it.status != CopyStatus.Spent }
}
