package io.issac.pleosandroid.core.cardui.preview

import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import java.time.Instant

// Use actual print/variant combinations so a preview gets the same finish as a newly drawn copy.
val previewCards = listOf(
    CardPrint(
        "seoul-gyeongbokgung", "경복궁의 아침", PackType.Seoul, PrintGroup.Landmark, CardGrade.Common,
        "print_seoul_gyeongbokgung_base", "print_seoul_gyeongbokgung_variant", "경복궁", "SEOUL / 001",
    ),
    CardPrint(
        "busan-haeundae", "해운대의 파도", PackType.Busan, PrintGroup.Beach, CardGrade.Common,
        "print_busan_haeundae_base", "print_busan_haeundae_variant", "해운대해수욕장", "BUSAN / 001",
    ),
    CardPrint(
        "jeju-dolhareubang", "제주의 돌하르방", PackType.Jeju, PrintGroup.Specialty, CardGrade.Rare,
        "print_jeju_dolhareubang_base", "print_jeju_dolhareubang_variant", "돌하르방", "JEJU / 009",
    ),
    CardPrint(
        "hyundai-motorstudio-goyang", "현대 모터스튜디오 고양", PackType.Hyundai, PrintGroup.Studio, CardGrade.HH,
        "print_hyundai_motorstudio_goyang_base", null, "일산서구", "HYUNDAI / 001",
    ),
    CardPrint(
        "epit-seoul-station", "서울역 E-pit", PackType.Hyundai, PrintGroup.Epit, CardGrade.H,
        "print_epit_seoul_station_base", null, "서울역", "HYUNDAI / 007",
    ),
).map { print ->
    val region = requireNotNull(print.packType)
    val pack = ReceivedPack("preview-pack-${region.name}", region, "연출 미리보기", Instant.EPOCH)
    CardCopy(
        "preview-card-${print.id}", print, region == PackType.Busan || region == PackType.Jeju,
        0, 0, CopyStatus.Owned, 0, pack, Instant.EPOCH, true,
    )
}

fun previewPackCard(region: PackType, grade: CardGrade? = null) = previewCards.first {
    it.pack.packType == region && (grade == null || it.grade == grade)
}

fun previewForgeCard(after: Int, outcome: ForgeOutcome) = previewPackCard(PackType.Busan).copy(
    level = after,
    highest = after,
    status = if (outcome == ForgeOutcome.Destroyed) CopyStatus.Trace else CopyStatus.Owned,
)

fun forgePreviewDetail(before: Int, after: Int, outcome: ForgeOutcome): String = when (outcome) {
    ForgeOutcome.GreatSuccess -> "+$before → +${before + 1} → +$after"
    ForgeOutcome.Destroyed -> "+$before → 흔적 +$after"
    ForgeOutcome.Restored -> "흔적 +$before → +$after"
    ForgeOutcome.Hold -> "+$before 유지"
    else -> "+$before → +$after"
}
