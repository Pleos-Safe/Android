package io.issac.pleosandroid.feature.badges.collection.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.issac.pleosandroid.core.designsystem.component.FullLayoutMinWidth
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.badges.collection.CardOrder
import io.issac.pleosandroid.feature.badges.collection.CollectionFilter
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.model.CollectionPlace

@Composable
internal fun CollectionFilterDialog(
    cards: List<CollectionCard>,
    filter: CollectionFilter,
    onApply: (CollectionFilter) -> Unit,
    onClose: () -> Unit,
    places: List<CollectionPlace> = cards.mapNotNull { it.place }.distinct(),
) {
    val kindLabels = CardKind.entries.associateWith { it.label }
    var draft by rememberSaveable(stateSaver = CollectionFilter.Saver) { mutableStateOf(filter) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val full = maxWidth >= FullLayoutMinWidth * PleosTextStyles.scale
            Box(
                Modifier.fillMaxSize().padding(
                    horizontal = PleosLayout.horizontalPadding(full),
                    vertical = PleosLayout.sectionGap,
                ),
                contentAlignment = if (full) Alignment.CenterEnd else Alignment.Center,
            ) {
                PleosInfoPanel(Modifier.widthIn(max = 600.dp * PleosTextStyles.scale).fillMaxWidth()) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle("필터와 정렬", Modifier.weight(1f))
                            PleosTextLink("닫기", onClose)
                        }
                        HorizontalDivider(color = PleosTheme.colors.borderDefault)
                        FilterChoices(
                            "카드 종류",
                            listOf("전체") + kindLabels.values,
                            draft.kind?.label ?: "전체",
                        ) {
                            draft = draft.copy(kind = kindLabels.entries.find { entry -> entry.value == it }?.key)
                        }
                        val placesEnabled = draft.kind != CardKind.Safety
                        FilterChoices(
                            "지역",
                            listOf("부산 전체") + places.map { it.district }.distinct(),
                            draft.district ?: "부산 전체",
                            placesEnabled,
                        ) {
                            draft = draft.copy(district = it.takeUnless { label -> label == "부산 전체" })
                        }
                        FilterChoices(
                            "장소 유형",
                            listOf("전체") + places.map { it.typeLabel }.distinct(),
                            draft.placeType ?: "전체",
                            placesEnabled,
                        ) {
                            draft = draft.copy(placeType = it.takeUnless { label -> label == "전체" })
                        }
                        FilterChoices("정렬", CardOrder.entries.map { it.label }, draft.order.label) {
                            draft = draft.copy(order = CardOrder.entries.first { order -> order.label == it })
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PleosButton("초기화", { draft = draft.clearedPlaces() }, style = PleosButtonStyle.Secondary)
                            PleosButton("카드 ${draft.select(cards).size}장 보기", {
                                onApply(draft)
                            }, Modifier.weight(1f), showArrow = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChoices(
    title: String,
    choices: List<String>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
        Caption(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { choice ->
                PleosChip(choice, choice == selected, { onSelect(choice) }, enabled = enabled)
            }
        }
    }
}
