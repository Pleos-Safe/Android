package io.issac.pleosandroid.feature.badges.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.component.pleosShellBackground
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardGrade
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.BadgesRoute
import io.issac.pleosandroid.core.navigation.DrivingReportRoute
import io.issac.pleosandroid.core.navigation.EnhancementRoute
import io.issac.pleosandroid.core.navigation.LocalNavigationRouter
import io.issac.pleosandroid.core.navigation.OpenPackRoute
import io.issac.pleosandroid.core.navigation.PackShelfRoute
import io.issac.pleosandroid.feature.badges.album.gallery.MotionGalleryRoute
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CollectionStatusScreen
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.navigation.collectionNavigationViewModel
import kotlinx.serialization.Serializable

@Serializable internal data object PackOddsRoute : AppRoute

@Serializable internal data class AlbumDetailRoute(val copyId: String) : AppRoute

@Serializable internal data class WorkshopRoute(val copyId: String) : AppRoute

@Composable
internal fun AlbumDestination(route: AppRoute, modifier: Modifier, viewModel: AlbumViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val album = state.album
    val router = LocalNavigationRouter.current
    val nav = collectionNavigationViewModel()
    val focus by nav.shelfTarget.collectAsStateWithLifecycle()
    val send = viewModel::handleIntent
    val back: () -> Unit = { router.pop() }
    val inbox: () -> Unit = { router.replaceAll(BadgesRoute, PackShelfRoute) }
    fun locate(id: String) {
        nav.focusCard(id)
        router.replaceAll(BadgesRoute)
    }
    val missing: @Composable () -> Unit = {
        CollectionStatusScreen("요청한 카드나 팩을 찾을 수 없어요.", modifier, onBack = back)
    }
    if (album == null) {
        CollectionStatusScreen(
            state.message ?: "카드 보관함을 불러오고 있어요",
            modifier,
            onRetry = if (state.message != null) ({ send(AlbumIntent.Retry) }) else null,
        )
        return
    }
    val modeControl: @Composable () -> Unit = {
        PleosTextLink(
            if (album.demo) "체험 모드 종료" else "체험 모드 시작",
            { send(AlbumIntent.Demo(!album.demo)) },
            enabled = !state.busy,
        )
    }
    when (route) {
        BadgesRoute, EnhancementRoute -> {
            val forge = route == EnhancementRoute
            AlbumShelf(
                album = album,
                forge = forge,
                focusId = focus.takeUnless { forge },
                onFocused = nav::targetHandled,
                packShelf = inbox,
                modeControl = modeControl,
                onPreviewMotions = { router.navigate(MotionGalleryRoute(startOnForge = forge)) },
                onCard = { card ->
                    val workshop = forge || card.status == CopyStatus.Trace || card.id in album.pending
                    router.navigate(if (workshop) WorkshopRoute(card.id) else AlbumDetailRoute(card.id))
                },
                modifier = modifier,
            )
        }

        PackShelfRoute -> PackShelf(
            album = album,
            onBack = back,
            open = { router.navigate(OpenPackRoute(it)) },
            chances = { router.navigate(PackOddsRoute) },
            demoControl = modeControl,
            modifier = modifier,
        )

        PackOddsRoute -> PackOdds(back, modifier)

        is OpenPackRoute -> {
            val pack = album.packs.find { it.id == route.packId }
            if (pack == null) {
                missing()
            } else {
                key(pack.id) {
                    PackOpening(pack, album.byPack[pack.id], state.busy, send, back, ::locate, modifier)
                }
            }
        }

        is AlbumDetailRoute -> {
            val card = album.collection.find { it.id == route.copyId }
            if (card == null) {
                missing()
            } else {
                CardDetails(
                    card,
                    album,
                    state.busy,
                    { send(AlbumIntent.Feature(card.id)) },
                    { router.replaceAll(EnhancementRoute, WorkshopRoute(card.id)) },
                    back,
                    modifier,
                )
            }
        }

        is WorkshopRoute -> {
            val card = album.collection.find { it.id == route.copyId }
            if (card == null) {
                missing()
            } else {
                key(card.id) { CardWorkshop(card, album, state.busy, send, back, ::locate, modifier) }
            }
        }
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = { send(AlbumIntent.Dismiss) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { send(AlbumIntent.Dismiss) }) { Text("확인") } },
        )
    }
}

@Composable
private fun CardDetails(
    card: CardCopy,
    album: AlbumSnapshot,
    busy: Boolean,
    featured: () -> Unit,
    forge: () -> Unit,
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
            val router = LocalNavigationRouter.current
            for (memory in card.travelMemories) {
                if (memory.journeyId == null) {
                    BodyText(memory.date.toString())
                } else {
                    PleosTextLink("${memory.date} · 여정 보기", {
                        router.navigate(DrivingReportRoute(requireNotNull(memory.journeyId)))
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
                Caption("${dateOf(entry.at)} · ${entry.outcome.label} · $delta · ${points(entry.costHalfPoints)}")
            }
            if (history.size > 3) PleosTextLink(if (all) "접기" else "기록 더 보기", { all = !all })
        }
    }
}

@Composable
private fun PackOdds(back: () -> Unit, modifier: Modifier) {
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
