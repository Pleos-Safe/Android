package io.issac.pleosandroid.feature.collection.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.issac.pleosandroid.core.cardui.component.BodyText
import io.issac.pleosandroid.core.cardui.component.Caption
import io.issac.pleosandroid.core.cardui.component.ExhibitScreen
import io.issac.pleosandroid.core.cardui.component.SectionTitle
import io.issac.pleosandroid.core.cardui.component.dateOf
import io.issac.pleosandroid.core.cardui.component.label
import io.issac.pleosandroid.core.cardui.print.CardPrintView
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.points.formatHalfPoints
import io.issac.pleosandroid.feature.collection.pack.title

@Composable
internal fun CardDetails(
    card: CardCopy,
    album: AlbumSnapshot,
    busy: Boolean,
    featured: () -> Unit,
    forge: () -> Unit,
    openJourney: (String) -> Unit,
    back: () -> Unit,
    modifier: Modifier,
) {
    val selected = album.featuredId == card.id
    ExhibitScreen("카드 상세", back, modifier, art = { CardPrintView(card, it, inspectable = true) }) {
        Caption("${card.grade} · " + if (card.variant) "색상 변종" else "기본색", accent = true)
        SectionTitle("${card.print.title}  +${card.level}")
        BodyText("획득일   ${dateOf(card.acquiredAt)}")
        BodyText("획득 경로   ${card.pack.source}")
        card.pack.place?.let { BodyText("획득 장소   $it") }
        BodyText("최고 강화   +${card.highest}")
        Caption("소장 번호  ${card.id.takeLast(8)}")
        PleosButton("강화하기", forge, Modifier.fillMaxWidth().testTag("album-enhance"), showArrow = true)
        PleosButton(
            if (selected) "대표 카드로 설정됨" else "대표 카드로 설정",
            featured,
            Modifier.fillMaxWidth(),
            enabled = !busy && !selected,
            style = PleosButtonStyle.Secondary,
        )
        if (card.travelMemories.isNotEmpty()) {
            SectionTitle("여행 기억")
            for (memory in card.travelMemories) {
                if (memory.journeyId == null) {
                    BodyText(memory.date.toString())
                } else {
                    PleosTextLink("${memory.date} · 여정 보기", {
                        openJourney(requireNotNull(memory.journeyId))
                    })
                }
            }
        }
        val history = album.history.filter { it.copyId == card.id }
        if (history.isNotEmpty()) {
            var all by androidx.compose.runtime.saveable.rememberSaveable(card.id) { mutableStateOf(false) }
            SectionTitle("강화 기록")
            for (entry in if (all) history else history.take(3)) {
                val delta = "+${entry.before} → +${entry.after}"
                Caption(
                    "${dateOf(entry.at)} · ${entry.outcome.label} · $delta · ${formatHalfPoints(entry.costHalfPoints)}",
                )
            }
            if (history.size > 3) PleosTextLink(if (all) "접기" else "기록 더 보기", { all = !all })
        }
    }
}
