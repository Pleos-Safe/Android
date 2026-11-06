package io.issac.pleosandroid.feature.badges.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.InspectableMilestoneCard
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.feature.badges.R
import io.issac.pleosandroid.feature.badges.component.ArtStage
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CardExhibit
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionColumns
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.model.displayDate
import io.issac.pleosandroid.feature.badges.navigation.CardMemoriesRoute
import io.issac.pleosandroid.feature.badges.navigation.CollectedPlaceRoute
import io.issac.pleosandroid.feature.badges.navigation.RepresentativeCardRoute
import io.issac.pleosandroid.feature.badges.navigation.RevealCardRoute

@Composable
internal fun CardDetailScreen(
    card: CollectionCard,
    featured: Boolean,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollectionPage(modifier) { window ->
        val full = window.full
        CollectionBack(stringResource(R.string.collection_title), onBack)
        val caption = when {
            card.pending -> "새로운 모습이 기다려요"
            featured -> "홈에 전시 중"
            else -> "${card.firstDate.displayDate()}부터"
        }
        CardExhibit(window, if (full) 284.dp else 214.dp, card.finishCaption(caption), print = {
            InspectableMilestoneCard(card.face(), it)
        }) {
            val actionsBesideDetails = window.tall && !full && window.width >= 720.dp * PleosTextStyles.scale
            CollectionColumns(
                actionsBesideDetails,
                leftWeight = 1.15f,
                first = { CardDescription(card, full) },
                second = { CardActions(card, onOpen) },
            )
        }
    }
}

@Composable
private fun CardDescription(card: CollectionCard, full: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
        Caption(
            card.place?.let {
                "부산 · ${it.displayName}"
            } ?: stringResource(R.string.card_detail_safety),
            accent = true,
        )
        CollectionTitle(card.publicTitle, full)
        Caption(card.design.subtitle)
        HorizontalDivider(color = PleosTheme.colors.borderDefault)
        Caption(stringResource(R.string.collection_first_collected))
        SectionTitle(card.firstDate.displayDate())
        SectionTitle(card.memorySummary)
        val last = card.lastDate
        val memoryType = if (card.place == null) "여정" else "기억"
        Caption("${last.monthValue}월 ${last.dayOfMonth}일 · 가장 최근에 남긴 $memoryType")
    }
}

@Composable
private fun CardActions(card: CollectionCard, onOpen: (AppRoute) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
        if (card.pending) {
            PleosButton("변화 만나보기", {
                onOpen(RevealCardRoute(card.id))
            }, Modifier.fillMaxWidth(), showArrow = true)
        }
        if (card.design.postcard && card.place != null) {
            PleosButton("대표 카드로 설정", {
                onOpen(RepresentativeCardRoute(card.id))
            }, Modifier.fillMaxWidth(), showArrow = true)
            PleosTextLink(stringResource(R.string.card_detail_memory), { onOpen(CardMemoriesRoute(card.id)) })
        } else {
            PleosButton(
                if (card.place == null) "함께한 여정 보기" else stringResource(R.string.card_detail_memory),
                { onOpen(CardMemoriesRoute(card.id)) },
                Modifier.fillMaxWidth(),
                showArrow = true,
            )
            PleosTextLink(stringResource(R.string.card_detail_change_featured), {
                onOpen(RepresentativeCardRoute(card.id))
            })
        }
        card.place?.let { place -> PleosTextLink("지도에서 보기", { onOpen(CollectedPlaceRoute(place.id)) }) }
    }
}

@Composable
internal fun CardMemoriesScreen(
    card: CollectionCard,
    onBack: () -> Unit,
    onRecord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollectionPage(modifier) { window ->
        val full = window.full
        CollectionBack(card.publicTitle, onBack)
        CollectionColumns(full, leftWeight = .55f, first = {
            if (full) {
                ArtStage(
                    card.face(),
                    window.printWidth(174.dp, 174.dp),
                    card.publicTitle,
                )
            } else {
                Caption(card.memorySummary, accent = true)
            }
        }, second = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PleosLayout.itemGap)) {
                CollectionTitle(if (card.place == null) "함께한 여정" else stringResource(R.string.card_detail_memory), full)
                card.memories.sortedByDescending { it.date }.forEachIndexed { index, memory ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SectionTitle(memory.date.dayOfMonth.toString())
                            Caption("${memory.date.monthValue}월")
                        }
                        Column(Modifier.weight(1f)) {
                            PleosTextLink(memory.title, { onRecord(memory.id) })
                            Caption(memory.description)
                            if (index == 0) Caption("가장 최근의 기억", accent = true)
                        }
                    }
                    HorizontalDivider(color = PleosTheme.colors.borderDefault)
                }
                PleosTextLink("카드로 돌아가기", onBack)
            }
        })
    }
}
