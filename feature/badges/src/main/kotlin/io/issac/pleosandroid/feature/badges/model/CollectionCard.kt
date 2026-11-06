package io.issac.pleosandroid.feature.badges.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.issac.pleosandroid.core.designsystem.component.MilestoneCardFace
import io.issac.pleosandroid.feature.badges.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class CardKind(@param:StringRes val labelRes: Int) {
    Place(R.string.collection_places),
    Safety(R.string.collection_safety),
    ;

    val label: String @Composable get() = stringResource(labelRes)
}

internal data class CollectionPlace(
    val id: String,
    val displayName: String,
    val district: String,
    val typeLabel: String,
    val latitude: Double,
    val longitude: Double,
    val mapX: Float? = null,
    val mapY: Float? = null,
) {
    companion object
}

internal data class CardMemory(
    val id: String,
    val date: LocalDate,
    val title: String,
    val description: String,
    val journeyId: String? = null,
)

internal data class CollectionCard(
    val design: CollectionCardDesign,
    val earnedLevel: Int,
    val revealedLevel: Int?,
    val memories: List<CardMemory>,
    val acquiredDate: LocalDate = LocalDate.of(2026, 1, 1),
) {
    val id get() = design.id
    val place get() = design.place
    val kind get() = if (place == null) CardKind.Safety else CardKind.Place
    val publicTitle get() = if (revealedLevel != null) design.title else place?.displayName ?: "일상의 여정이 남긴 한 장"
    val pending get() = revealedLevel != earnedLevel
    val revealLabel: String
        @Composable get() = if (revealedLevel == null) {
            stringResource(R.string.collection_unopened)
        } else {
            stringResource(R.string.collection_evolution_levels, revealedLevel, earnedLevel)
        }
    val firstDate = memories.minOfOrNull { it.date } ?: acquiredDate
    val lastDate = memories.maxOfOrNull { it.date } ?: acquiredDate
    val memorySummary get() = if (place == null) "함께 남긴 여정 ${memories.size}개" else "함께 남긴 기억 ${memories.size}개"

    @Composable
    fun face(level: Int? = revealedLevel): MilestoneCardFace =
        if (level == null) MilestoneCardFace.CommonBack else design.front(level)

    @Composable
    fun finishCaption(caption: String): String = if ((revealedLevel ?: 0) >= 3) {
        stringResource(R.string.collection_finish_caption, caption)
    } else {
        caption
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.KOREA)
internal fun LocalDate.displayDate(): String = format(dateFormat)
internal fun LocalDate.shortDate(): String = displayDate().substringAfter('.')
