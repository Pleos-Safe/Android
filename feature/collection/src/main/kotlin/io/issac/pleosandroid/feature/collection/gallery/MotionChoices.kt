package io.issac.pleosandroid.feature.collection.gallery

import io.issac.pleosandroid.core.cardui.component.label
import io.issac.pleosandroid.core.cardui.preview.forgePreviewDetail
import io.issac.pleosandroid.core.cardui.preview.previewCards
import io.issac.pleosandroid.core.cardui.preview.previewPackCard
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.navigation.CardMotionRoute
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.core.navigation.PackMotionRoute

internal enum class MotionGroup(val title: String) {
    Packs("카드팩"),
    Outcomes("강화 결과"),
    Milestones("강화 단계"),
}

internal val CardMotionRoute.title: String get() = when (this) {
    is PackMotionRoute -> if (flipOnly) "${previewPackCard(region, grade).grade} 카드 뒤집기" else region.packTitle
    is ForgeMotionRoute -> outcome.label
}

internal val CardMotionRoute.detail: String get() = when (this) {
    is PackMotionRoute -> if (flipOnly) previewPackCard(region, grade).print.title else "봉투 개봉 → 카드 뒤집기"
    is ForgeMotionRoute -> forgePreviewDetail(before, after, outcome)
}

internal data class MotionChoice(
    val scene: CardMotionRoute,
    val title: String = scene.title,
    val detail: String = scene.detail,
    val enabled: Boolean = true,
)

internal fun motionChoices(group: MotionGroup, before: Int): List<MotionChoice> = when (group) {
    MotionGroup.Packs -> PackType.entries.map { MotionChoice(PackMotionRoute(it)) } +
        previewCards.sortedBy { it.grade }.map {
            MotionChoice(PackMotionRoute(requireNotNull(it.pack.packType), flipOnly = true, grade = it.grade))
        }

    MotionGroup.Outcomes -> {
        val odds = CardRules.odds(before)
        listOf(
            ForgeOutcome.Success to (odds.success > 0),
            ForgeOutcome.GreatSuccess to (odds.great > 0),
            ForgeOutcome.Hold to true,
            ForgeOutcome.Drop to (odds.drop > 0),
            ForgeOutcome.Destroyed to (before >= 10),
            ForgeOutcome.Restored to true,
        ).map { (outcome, enabled) ->
            val scene = ForgeMotionRoute(before, CardRules.nextLevel(before, outcome).coerceAtLeast(0), outcome)
            val detail = when {
                !enabled -> "이 단계에서는 발생하지 않아요"

                (outcome == ForgeOutcome.Hold && odds.hold == 0) ||
                    (outcome == ForgeOutcome.Destroyed && odds.destroy == 0) -> "${scene.detail} · 실제 발생 확률 0%"

                else -> scene.detail
            }
            MotionChoice(scene, detail = detail, enabled = enabled)
        }
    }

    MotionGroup.Milestones -> listOf(5, 10, 15, 20, 25, 26, 27, 28).map { target ->
        val scene = ForgeMotionRoute(target - 1, target, ForgeOutcome.Success)
        val finish = when (target) {
            26 -> "초월 · 일식"
            27 -> "초월 · 오로라"
            28 -> "초월 · 별자리"
            else -> "프레임 진화"
        }
        MotionChoice(scene, scene.detail, finish)
    }
}
