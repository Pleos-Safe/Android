package io.issac.pleosandroid.feature.badges.album.gallery

import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardPrint
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.cards.PrintGroup
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.feature.badges.album.label
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable internal data class MotionGalleryRoute(val startOnForge: Boolean = false) : AppRoute

@Serializable
internal sealed interface CardMotion : AppRoute {
    @Serializable data class Pack(val region: PackType, val flipOnly: Boolean = false) : CardMotion

    @Serializable data class Forge(val before: Int, val after: Int, val outcome: ForgeOutcome) : CardMotion
}

internal enum class MotionGroup(val title: String) {
    Packs("카드팩"),
    Outcomes("강화 결과"),
    Milestones("강화 단계"),
}

internal val CardMotion.title: String get() = when (this) {
    is CardMotion.Pack -> if (flipOnly) "카드 뒤집기" else region.packTitle
    is CardMotion.Forge -> outcome.label
}

internal val CardMotion.detail: String get() = when (this) {
    is CardMotion.Pack -> if (flipOnly) "뒷면 → 앞면" else "봉투 개봉 → 카드 뒤집기"

    is CardMotion.Forge -> when (outcome) {
        ForgeOutcome.GreatSuccess -> "+$before → +${before + 1} → +$after"
        ForgeOutcome.Destroyed -> "+$before → 흔적 +$after"
        ForgeOutcome.Restored -> "흔적 +$before → +$after"
        ForgeOutcome.Hold -> "+$before 유지"
        else -> "+$before → +$after"
    }
}

internal data class MotionChoice(
    val scene: CardMotion,
    val title: String = scene.title,
    val detail: String = scene.detail,
    val enabled: Boolean = true,
)

internal fun motionChoices(group: MotionGroup, before: Int): List<MotionChoice> = when (group) {
    MotionGroup.Packs -> PackType.entries.map { MotionChoice(CardMotion.Pack(it)) } +
        MotionChoice(CardMotion.Pack(PackType.Busan, flipOnly = true))

    MotionGroup.Outcomes -> {
        val odds = CardRules.odds(before)
        listOf(
            ForgeOutcome.Success to (odds.success > 0),
            ForgeOutcome.GreatSuccess to (odds.great > 0),
            ForgeOutcome.Hold to (odds.hold > 0),
            ForgeOutcome.Drop to (odds.drop > 0),
            ForgeOutcome.Destroyed to (odds.destroy > 0),
            ForgeOutcome.Restored to true,
        ).map { (outcome, enabled) ->
            val scene = CardMotion.Forge(before, CardRules.nextLevel(before, outcome).coerceAtLeast(0), outcome)
            val detail = if (enabled) scene.detail else "이 단계에서는 발생하지 않아요"
            MotionChoice(scene, detail = detail, enabled = enabled)
        }
    }

    MotionGroup.Milestones -> listOf(5, 10, 15, 20, 25, 26, 27, 28).map { target ->
        val scene = CardMotion.Forge(target - 1, target, ForgeOutcome.Success)
        val finish = when (target) {
            26 -> "초월 · 일식"
            27 -> "초월 · 오로라"
            28 -> "초월 · 별자리"
            else -> "프레임 진화"
        }
        MotionChoice(scene, scene.detail, finish)
    }
}

// These are display-only copies. Each pack illustrates a different region and card grade.
private val previewPrints = listOf(
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
).associateBy { it.packType }

internal fun motionCard(region: PackType, level: Int = 0, status: CopyStatus = CopyStatus.Owned): CardCopy {
    val pack = ReceivedPack("preview-pack-${region.name}", region, "연출 미리보기", Instant.EPOCH)
    return CardCopy(
        "preview-card-${region.name}", previewPrints.getValue(region),
        region == PackType.Busan || region == PackType.Jeju, level, level,
        status, 0, pack, Instant.EPOCH, true,
    )
}

internal fun CardMotion.Forge.sample() = motionCard(
    PackType.Busan,
    after,
    if (outcome == ForgeOutcome.Destroyed) CopyStatus.Trace else CopyStatus.Owned,
)
