package io.issac.pleosandroid.feature.badges

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardAppearance
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardFace

internal enum class CardKind { Place, Safety }

internal data class CollectionCard(
    val id: String,
    val kind: CardKind,
    val appearance: MilestoneCardAppearance,
    @param:DrawableRes val artwork: Int,
    val context: String,
    val title: String,
    val subtitle: String,
    val series: String,
    val category: String?,
    val firstCollectedOn: String,
    val memorySummary: String,
    val latestMemory: String,
    val description: String,
    val featured: Boolean = false,
    val regionId: String? = null,
)

@Composable
internal fun CollectionCard.face() = MilestoneCardFace.Front(
    appearance = appearance,
    artwork = painterResource(artwork),
    contextLabel = context,
    title = title,
    subtitle = subtitle,
    seriesLabel = series,
    categoryLabel = category,
)

internal object SampleCollection {
    val cards = listOf(
        CollectionCard(
            id = "place-haeundae-001",
            kind = CardKind.Place,
            appearance = MilestoneCardAppearance.PlaceLevel3,
            artwork = R.drawable.milestone_art_haeundae,
            context = "부산 · 해운대",
            title = "해운대의 파도",
            subtitle = "부산의 여름을 간직하다",
            series = "MILESTONE / 001",
            category = "장소의 기억",
            firstCollectedOn = "2026.10.18",
            memorySummary = "함께 남긴 기억 3개",
            latestMemory = "11월 2일 · 가장 최근에 남긴 기억",
            description = "다시 만난 바다의 순간들.\n이 한 장에 차곡차곡 남아 있어요.",
            featured = true,
            regionId = "KR-26",
        ),
        CollectionCard(
            id = "safety-smooth-001",
            kind = CardKind.Safety,
            appearance = MilestoneCardAppearance.SafetyLevel2,
            artwork = R.drawable.milestone_art_safety,
            context = "운전습관 · 귀가",
            title = "안전한 귀가",
            subtitle = "여정을 차분히 마친 기록",
            series = "SAFETY / SMOOTH / 001",
            category = null,
            firstCollectedOn = "2026.10.25",
            memorySummary = "함께 남긴 여정 2개",
            latestMemory = "11월 2일 · 가장 최근에 남긴 여정",
            description = "여정을 차분히 마친 기록이에요.",
        ),
    )
}
