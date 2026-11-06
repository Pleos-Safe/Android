package io.issac.pleosandroid.feature.badges.reveal

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
import io.issac.pleosandroid.feature.badges.component.CardExhibit
import io.issac.pleosandroid.feature.badges.component.CollectionBack
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.model.displayDate
import io.issac.pleosandroid.feature.badges.reveal.motion.CardRevealMotion
import io.issac.pleosandroid.feature.badges.reveal.motion.RevealCardPrint

@Composable
internal fun CollectionRevealScreen(
    card: CollectionCard,
    onBack: () -> Unit,
    onViewCollection: () -> Unit,
    onSetRepresentative: () -> Unit,
    onLater: () -> Unit,
    onComplete: (Int) -> Unit,
    canOpen: () -> Boolean,
    modifier: Modifier = Modifier,
    failedAttempt: Int = 0,
    saveError: String? = null,
) {
    val motion = CardRevealMotion.forCard(card)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mayOpen by rememberUpdatedState(canOpen)
    val complete by rememberUpdatedState(onComplete)
    var playback by remember(card.id, card.earnedLevel, card.revealedLevel, failedAttempt) {
        mutableStateOf<AnimationState<Float, AnimationVector1D>?>(null)
    }
    val run = playback
    // Both prints and the published result keep one light clock, including across a window resize.
    val light = key(card.id) {
        rememberMilestoneCardLight((card.revealedLevel ?: 0) >= 4 || (run != null && card.earnedLevel >= 4))
    }
    val lightAngle = remember(light) { { light.value } }
    if (run != null) {
        LifecycleResumeEffect(run) {
            onPauseOrDispose { if (playback === run) playback = null }
        }
        LaunchedEffect(run) {
            fun isActive() = playback === run && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && mayOpen()

            run.animateTo(1f, tween(motion.durationMillis, easing = LinearEasing)) {
                if (!isActive()) cancelAnimation()
            }
            if (isActive() && run.value == 1f) {
                // Present the resting front for a frame, including when the system skips animations.
                withFrameNanos { }
                withFrameNanos { }
                if (isActive()) {
                    complete(card.earnedLevel)
                    return@LaunchedEffect
                }
            }
            playback = null
        }
    }
    CollectionPage(modifier) { window ->
        val full = window.full
        val status = when {
            !card.pending -> "카드를 만났어요"
            run == null -> "열어볼 준비가 됐어요"
            motion != CardRevealMotion.Flip -> "새로운 모습으로 진화 중"
            else -> "카드를 여는 중"
        }
        Row(
            Modifier.animateContentSize(tween(620, easing = FastOutSlowInEasing)).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CollectionBack(stringResource(R.string.collection_go_back)) {
                playback = null
                onBack()
            }
            Caption(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, accent = true)
        }
        CardExhibit(
            window,
            if (full) 284.dp else 212.dp,
            "",
            Modifier.testTag(
                "collection-reveal-stage",
            ),
            print = { space ->
                // Keep the resting print on its shelf and leave its upper corners room to recoil.
                val restingWidth = when (motion) {
                    CardRevealMotion.Opal -> .96f
                    CardRevealMotion.Gold -> .94f
                    else -> 1f
                }
                Box(
                    space.aspectRatio(MILESTONE_CARD_ASPECT_RATIO).testTag("collection-reveal-card"),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val print = Modifier.fillMaxWidth(restingWidth)
                    when {
                        run != null -> RevealCardPrint(card, motion, run, lightAngle, print)
                        card.pending -> MilestoneCard(card.face(), print, lightAngle)
                        else -> InspectableMilestoneCard(card.face(), print, lightAngle)
                    }
                }
            },
        ) {
            Column(
                Modifier.animateContentSize(tween(620, easing = FastOutSlowInEasing)).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (saveError != null) Caption(saveError)
                if (!card.pending) {
                    CollectionTitle(card.design.title, full)
                    Caption(card.design.subtitle)
                    Caption(
                        card.finishCaption("Lv.${card.earnedLevel} · ${card.lastDate.displayDate()}"),
                        accent = true,
                    )
                    PleosButton(
                        "컬렉션에서 보기",
                        onViewCollection,
                        Modifier.fillMaxWidth(),
                        showArrow = true,
                    )
                    PleosTextLink("대표 카드로 설정", onSetRepresentative)
                } else {
                    val growth = card.revealedLevel != null
                    CollectionTitle(if (growth) "내 카드에 새로운 모습이" else "${card.publicTitle}의 한 장이 도착했어요", full)
                    Caption(if (growth) card.revealLabel else "오늘의 기억을 열어볼까요?", accent = growth)
                    PleosButton(
                        if (growth) "변화 만나보기" else "카드 열기",
                        { if (playback == null && mayOpen()) playback = AnimationState(0f) },
                        Modifier.fillMaxWidth(),
                        enabled = run == null,
                        showArrow = true,
                    )
                    PleosTextLink("나중에 열기", {
                        playback = null
                        onLater()
                    })
                }
            }
        }
    }
}
