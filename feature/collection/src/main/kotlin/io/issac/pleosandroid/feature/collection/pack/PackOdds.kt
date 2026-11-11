package io.issac.pleosandroid.feature.collection.pack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.cardui.component.AlbumHeading
import io.issac.pleosandroid.core.cardui.component.AlbumPanel
import io.issac.pleosandroid.core.cardui.component.BodyText
import io.issac.pleosandroid.core.cardui.component.Caption
import io.issac.pleosandroid.core.cardui.component.SectionTitle
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.PackType

@Composable
internal fun PackOdds(back: () -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().pleosShellBackground()) {
        val wide = maxWidth >= PleosLayout.twoPaneMinWidth
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PleosLayout.panelPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AlbumHeading("카드팩 확률", wide, back = back)
            val percent = java.text.NumberFormat.getPercentInstance(java.util.Locale.KOREA).apply {
                maximumFractionDigits = 2
            }
            for ((odds, packs) in PackType.entries.groupBy(CardRules::packOdds)) {
                AlbumPanel {
                    SectionTitle(packs.joinToString(" · ") { it.packTitle })
                    BodyText(odds.weights.entries.joinToString(" · ") { (group, weight) -> "${group.title} $weight%" })
                    Caption(
                        CardGrade.entries.filter { odds.gradeChance(it) > 0 }.joinToString(" · ") {
                            "${it.name} ${percent.format(odds.gradeChance(it) / 10000.0)}"
                        },
                    )
                    if (odds.variantPercent > 0) {
                        Caption("색상 변종 ${odds.variantPercent}% · 등급 한 단계 상승")
                    } else {
                        Caption("스튜디오 HH · E-pit H · 기본 원화만 등장")
                    }
                }
            }
            Caption("같은 분류 안에서는 각 카드의 확률이 같아요. 중복 카드는 한 장씩 따로 소장해요.")
        }
    }
}
