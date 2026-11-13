package io.issac.pleosandroid.feature.collection.pack

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.issac.pleosandroid.core.cardui.component.BodyText
import io.issac.pleosandroid.core.cardui.component.Caption
import io.issac.pleosandroid.core.cardui.component.ExhibitScreen
import io.issac.pleosandroid.core.cardui.component.SectionTitle
import io.issac.pleosandroid.core.cardui.motion.smoothStep
import io.issac.pleosandroid.core.cardui.print.CardPrintView
import io.issac.pleosandroid.core.cardui.print.PreparedCardPrint
import io.issac.pleosandroid.core.cardui.print.packFlipEdge
import io.issac.pleosandroid.core.cardui.print.rememberPreparedCardPrint
import io.issac.pleosandroid.core.designsystem.R as DesignR
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.ReceivedPack
import io.issac.pleosandroid.feature.collection.CollectionIntent
import kotlin.math.sin

internal const val PACK_TEAR_MS = 2140
internal const val PACK_FLIP_MS = 760

@Composable
internal fun PackOpening(
    pack: ReceivedPack,
    card: CardCopy?,
    busy: Boolean,
    send: (CollectionIntent) -> Unit,
    onBack: () -> Unit,
    inCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only the actions, not navigation or animation state, change the inventory. Returning after a
    // draw always resumes at the back of that copy, even after process recreation.
    var openedHere by remember(pack.id) { mutableStateOf(false) }
    var turning by remember(pack.id) { mutableStateOf(false) }
    val tear = remember(pack.id) { Animatable(if (card == null) 0f else 1f) }
    val angle = remember(pack.id) { Animatable(if (card?.seen == true) 180f else 0f) }
    val prepared = rememberPreparedCardPrint(card)
    LaunchedEffect(card?.id) {
        if (card != null) {
            if (openedHere) tear.animateTo(1f, tween(PACK_TEAR_MS, easing = LinearEasing)) else tear.snapTo(1f)
        }
    }
    LaunchedEffect(turning, prepared) {
        if (turning && card != null && prepared != null && !card.seen) {
            angle.animateTo(180f, tween(PACK_FLIP_MS, easing = FastOutSlowInEasing))
            send(CollectionIntent.Reveal(card.id))
            turning = false
        }
    }
    val torn by remember(tear) { derivedStateOf { tear.value >= 1f } }
    val halfway by remember(angle) { derivedStateOf { angle.value >= 90f } }
    val shown = card?.seen == true
    val canTurn = card != null && torn && !shown && !turning && !busy
    val flip = { turning = true }
    val unseal = {
        openedHere = true
        send(CollectionIntent.Open(pack.id))
    }
    ExhibitScreen(if (shown) "새로운 카드" else pack.title, onBack, modifier, art = { art ->
        PackOpeningPrint(
            pack,
            card,
            tear.asState(),
            angle.asState(),
            shown,
            art,
            prepared = prepared,
            onUnseal = if (card == null && !busy) unseal else null,
            onFlip = if (canTurn) flip else null,
        )
    }) {
        if (shown) {
            Caption("${card.grade} · " + if (card.variant) "색상 변종" else "기본색", accent = true)
            SectionTitle(card.print.title)
            BodyText("${pack.title}에서 얻은 카드를 컬렉션에 담았어요.")
            PleosButton("컬렉션에서 보기", {
                inCollection(card.id)
            }, Modifier.fillMaxWidth().testTag("album-show-copy"), showArrow = true)
            PleosButton("팩 보관함", onBack, Modifier.fillMaxWidth(), style = PleosButtonStyle.Secondary)
        } else {
            if (card == null || !torn) {
                Caption("밀봉 팩", accent = true)
                SectionTitle("봉투를 열어 볼까요?")
                BodyText("이 안에 여행 카드 한 장이 들어 있어요.")
                PleosButton(
                    if (card == null && !busy) "봉투 열기" else "봉투 여는 중…",
                    unseal,
                    Modifier.fillMaxWidth().testTag("album-open-seal"),
                    enabled = card == null && !busy,
                    showArrow = true,
                )
            } else {
                Caption("카드 1장", accent = true)
                SectionTitle("어떤 여행이 담겼을까요?")
                BodyText("카드를 뒤집어 첫 장면을 만나 보세요.")
                PleosButton(
                    if (turning || busy) {
                        "뒤집는 중…"
                    } else if (halfway) {
                        "다시 확인"
                    } else {
                        "카드 뒤집기"
                    },
                    flip,
                    Modifier.fillMaxWidth().testTag("album-flip"),
                    enabled = canTurn,
                    showArrow = true,
                )
            }
            PleosButton("나중에 확인", onBack, Modifier.fillMaxWidth(), style = PleosButtonStyle.Secondary)
        }
    }
}

@Composable
internal fun PackOpeningPrint(
    pack: ReceivedPack,
    card: CardCopy?,
    tear: State<Float>,
    angle: State<Float>,
    shown: Boolean,
    modifier: Modifier,
    prepared: PreparedCardPrint?,
    onUnseal: (() -> Unit)? = null,
    onFlip: (() -> Unit)? = null,
) {
    val torn by remember(tear) { derivedStateOf { tear.value >= 1f } }
    val halfway by remember(angle) { derivedStateOf { angle.value >= 90f } }
    val stage = if (card != null && torn && !shown) modifier.packFlipEdge(card.grade, angle) else modifier
    Box(stage, contentAlignment = Alignment.Center) {
        if (card != null) {
            val face = Modifier.fillMaxSize().graphicsLayer {
                val t = tear.value
                val emerge = smoothStep(.43f, .88f, t)
                val settle = smoothStep(.78f, 1f, t)
                val rotation = if (shown) 180f else angle.value
                val lift = sin(rotation / 180f * Math.PI).toFloat()
                alpha = smoothStep(.42f, .50f, t)
                scaleX = .75f + .25f * settle + .035f * lift
                scaleY = scaleX
                rotationY = if (rotation < 90f) rotation else rotation - 180f
                rotationZ = -2.4f * emerge * (1f - settle)
                translationY = size.height * (.044f - .119f * emerge + .075f * settle - .025f * lift)
                cameraDistance = 12f * density
            }.then(if (onFlip == null) Modifier else Modifier.customClickable(onClick = onFlip))
            val showFront = prepared != null && (shown || halfway)
            Box(face) {
                if (prepared != null) {
                    val visibility = if (showFront) Modifier else Modifier.clearAndSetSemantics {}
                    Box(
                        Modifier.fillMaxSize().then(visibility).drawWithContent {
                            if (showFront) {
                                drawContent()
                            } else {
                                // Prime the foil on the GPU under one opaque pixel of the back.
                                clipRect(center.x, center.y, center.x + 1f, center.y + 1f) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                        },
                    ) {
                        CardPrintView(
                            card,
                            Modifier.fillMaxSize(),
                            inspectable = shown,
                            flipAngle = angle.takeUnless { shown },
                            prepared = prepared,
                        )
                    }
                }
                if (!showFront) {
                    Image(
                        painterResource(DesignR.drawable.chagok_common_back),
                        if (torn && !shown) "카드 뒷면" else null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                    )
                }
            }
        }
        if (!torn) {
            val interaction = if (card == null) {
                Modifier.semantics { contentDescription = "밀봉된 ${pack.title}" }
                    .then(if (onUnseal == null) Modifier else Modifier.customClickable(onClick = onUnseal))
            } else {
                Modifier
            }
            PackTear(pack, Modifier.fillMaxSize().then(interaction)) { tear.value }
        }
    }
}
