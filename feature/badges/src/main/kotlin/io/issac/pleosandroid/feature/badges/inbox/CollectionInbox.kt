package io.issac.pleosandroid.feature.badges.inbox

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.issac.pleosandroid.core.designsystem.component.InspectableMilestoneCard
import io.issac.pleosandroid.core.designsystem.component.MILESTONE_CARD_ASPECT_RATIO
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.rememberMilestoneCardLight
import io.issac.pleosandroid.feature.badges.R
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.component.DisplayedArt
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.component.VaultStage
import io.issac.pleosandroid.feature.badges.model.CollectionCard

@Composable
internal fun CollectionInbox(
    cards: List<CollectionCard>,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRestoreSamples: (() -> Unit)? = null,
) {
    CollectionPage(modifier) { window ->
        val full = window.full
        CollectionBack(stringResource(R.string.collection_title), onBack)
        Caption("${cards.size}장의 카드가 기다려요", accent = true)
        CollectionTitle("아직 열지 않은 기억", full)
        if (cards.isEmpty()) Caption("기다리는 카드를 모두 열었어요.")
        val columns = if (full) 2 else 1
        val rows = cards.sortedBy { it.revealedLevel ?: 0 }.chunked(columns)
        val fitRows = window.tall && (rows.size <= 2 || (full && rows.size == 3 && window.height >= 960.dp))
        rows.forEach { row ->
            Row(
                if (fitRows) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                row.forEach { card ->
                    val tile = if (fitRows) Modifier.weight(1f).fillMaxHeight() else Modifier.weight(1f)
                    VaultStage(tile, highlighted = card.revealedLevel != null) {
                        val isNew = card.revealedLevel == null
                        val label: @Composable () -> Unit = {
                            Caption(
                                if (isNew) {
                                    card.revealLabel
                                } else {
                                    stringResource(
                                        R.string.collection_evolution_pending,
                                        card.revealLabel,
                                    )
                                },
                                accent = true,
                            )
                            SectionTitle(card.publicTitle)
                        }
                        val open: @Composable () -> Unit = {
                            PleosButton(if (isNew) "카드 열기" else "변화 만나보기", { onSelect(card.id) }, showArrow = true)
                        }
                        if (fitRows && rows.size == 1) {
                            Column(
                                Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                DisplayedArt(card.face(), Dp.Infinity, Modifier.fillMaxWidth().weight(1f))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) { label() }
                                    open()
                                }
                            }
                        } else {
                            Row(
                                (if (fitRows) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).padding(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(.68f), contentAlignment = Alignment.Center) {
                                    val width = if (fitRows) {
                                        Dp.Infinity
                                    } else {
                                        window.printWidth(
                                            168.dp,
                                            104.dp,
                                            1f / rows.size,
                                        )
                                    }
                                    DisplayedArt(card.face(), width)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    label()
                                    open()
                                }
                            }
                        }
                    }
                }
                if (rows.size > 1 && row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
        PleosTextLink("컬렉션으로", onBack)
        if (onRestoreSamples !=
            null
        ) {
            PleosTextLink(stringResource(R.string.collection_restore_samples), onRestoreSamples)
        }
    }
}
