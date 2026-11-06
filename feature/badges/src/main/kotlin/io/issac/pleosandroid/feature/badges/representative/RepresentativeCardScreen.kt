package io.issac.pleosandroid.feature.badges.representative

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.MILESTONE_CARD_ASPECT_RATIO
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosShape
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.component.DisplayedArt
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.component.VaultStage
import io.issac.pleosandroid.feature.badges.model.CollectionCard

@Composable
internal fun RepresentativeCardScreen(
    cards: List<CollectionCard>,
    currentId: String?,
    initialId: String?,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
) {
    val eligible = cards.filter {
        it.revealedLevel != null
    }.sortedWith(compareBy(CollectionCard::firstDate, CollectionCard::id))
    var draft by rememberSaveable { mutableStateOf(initialId ?: currentId) }
    val index = eligible.indexOfFirst { it.id == draft }.coerceAtLeast(0)
    val choice = eligible.getOrNull(index)
    CollectionPage(modifier, compactSpacing = 8.dp) { window ->
        val full = window.full
        CollectionBack("대표 카드 선택", onBack)
        if (saving) Caption("선택을 저장하고 있어요")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = if (full) "홈에 전시할 한 장을 골라보세요." else "홈에 전시할 카드"
            CollectionTitle(title, full, Modifier.weight(1f))
            if (choice != null) {
                Caption(if (full) "선택할 수 있는 카드 ${eligible.size}장" else "${index + 1} / ${eligible.size}")
            }
        }
        if (choice == null) {
            Caption("카드를 열면 홈에 전시할 수 있어요.")
            return@CollectionPage
        }
        val preview: @Composable () -> Unit = {
            val artWidth =
                minOf(
                    ((window.height - 356.dp) * MILESTONE_CARD_ASPECT_RATIO).coerceAtLeast(80.dp),
                    window.printWidth(216.dp, 200.dp),
                )
            RepresentativePreview(choice, full, artWidth, onConfirm = { if (!saving) onConfirm(choice.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PleosButton(
                        text = "이전 카드",
                        onClick = { draft = eligible[index - 1].id },
                        enabled = index > 0,
                        style = PleosButtonStyle.Secondary,
                    )
                    PleosButton(
                        text = "다음 카드",
                        onClick = { draft = eligible[index + 1].id },
                        enabled = index < eligible.lastIndex,
                        style = PleosButtonStyle.Secondary,
                    )
                }
            }
        }
        if (full) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PleosLayout.columnGap)) {
                Column(Modifier.weight(1.9f), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                    eligible.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                            row.forEach { card ->
                                RepresentativeOption(
                                    card,
                                    artWidth = window.printWidth(100.dp, 100.dp, heightShare = .4f),
                                    chosen = card.id == choice.id,
                                    current = card.id == currentId,
                                    onChoose = { draft = card.id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Box(Modifier.weight(1f)) { preview() }
            }
        } else {
            preview()
        }
    }
}

@Composable
private fun RepresentativePreview(
    card: CollectionCard,
    full: Boolean,
    artWidth: Dp,
    onConfirm: () -> Unit,
    controls: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp * PleosTextStyles.scale)) {
        VaultStage(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(PleosLayout.itemGap),
                verticalArrangement = Arrangement.spacedBy(8.dp * PleosTextStyles.scale),
            ) {
                if (full) Caption("홈에 이렇게 보여요")
                DisplayedArt(card.face(), artWidth, Modifier.align(Alignment.CenterHorizontally))
                SectionTitle(card.publicTitle)
            }
        }
        if (!full) controls()
        PleosButton("이 카드로 전시하기", onConfirm, Modifier.fillMaxWidth(), showArrow = true)
    }
}

@Composable
private fun RepresentativeOption(
    card: CollectionCard,
    artWidth: Dp,
    chosen: Boolean,
    current: Boolean,
    onChoose: () -> Unit,
    modifier: Modifier,
) {
    VaultStage(
        modifier.testTag("representative-${card.id}")
            .customClickable(shape = PleosShape.stage, role = Role.RadioButton, onClick = onChoose)
            .semantics { selected = chosen },
        highlighted = chosen,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(PleosLayout.itemGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp * PleosTextStyles.scale),
        ) {
            Box(Modifier.widthIn(max = artWidth).fillMaxWidth()) { MilestoneCard(card.face()) }
            Caption(card.publicTitle, accent = chosen)
            if (chosen || current) Caption(if (chosen) "✓ 선택" else "현재 전시 중", accent = chosen)
        }
    }
}
