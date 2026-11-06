package io.issac.pleosandroid.feature.badges.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.MILESTONE_CARD_ASPECT_RATIO
import io.issac.pleosandroid.core.designsystem.component.MilestoneCard
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.modifier.customClickable
import io.issac.pleosandroid.core.navigation.AppRoute
import io.issac.pleosandroid.core.navigation.CardDetailRoute
import io.issac.pleosandroid.feature.badges.R
import io.issac.pleosandroid.feature.badges.collection.component.CollectionFilterDialog
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.CardExhibit
import io.issac.pleosandroid.feature.badges.component.CollectionColumns
import io.issac.pleosandroid.feature.badges.component.CollectionPage
import io.issac.pleosandroid.feature.badges.component.CollectionTitle
import io.issac.pleosandroid.feature.badges.component.CollectionWindow
import io.issac.pleosandroid.feature.badges.component.DisplayedArt
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import io.issac.pleosandroid.feature.badges.component.VaultStage
import io.issac.pleosandroid.feature.badges.guide.EmptyCollection
import io.issac.pleosandroid.feature.badges.model.CardKind
import io.issac.pleosandroid.feature.badges.model.CollectionCard
import io.issac.pleosandroid.feature.badges.model.CollectionPlace
import io.issac.pleosandroid.feature.badges.model.displayDate
import io.issac.pleosandroid.feature.badges.model.shortDate
import io.issac.pleosandroid.feature.badges.navigation.CollectionGuideRoute
import io.issac.pleosandroid.feature.badges.navigation.RepresentativeCardRoute
import io.issac.pleosandroid.feature.badges.navigation.RevealCardRoute
import io.issac.pleosandroid.feature.badges.navigation.UnopenedCardsRoute

@Composable
internal fun CollectionScreen(
    cards: List<CollectionCard>,
    representativeId: String?,
    onOpen: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
    canRevisitReveals: Boolean = false,
    shelfTarget: String? = null,
    onShelfTargetHandled: (String) -> Unit = {},
    selectedFilter: CollectionFilter? = null,
    onFilterChanged: ((CollectionFilter) -> Unit)? = null,
    places: List<CollectionPlace> = cards.mapNotNull { it.place }.distinct(),
    demoLabel: String? = null,
    onDemoClick: () -> Unit = {},
) {
    var previewFilter by rememberSaveable(stateSaver = CollectionFilter.Saver) { mutableStateOf(CollectionFilter()) }
    val filter = selectedFilter ?: previewFilter
    val setFilter: (CollectionFilter) -> Unit = onFilterChanged ?: { previewFilter = it }
    var editing by rememberSaveable { mutableStateOf(false) }
    var anchors by rememberSaveable { mutableStateOf<Map<String, ShelfAnchor>>(emptyMap()) }
    LaunchedEffect(shelfTarget, cards) {
        if (shelfTarget != null) {
            cards.find { it.id == shelfTarget }?.let { card ->
                setFilter(filter.showing(card))
                anchors = anchors + (card.kind.name to filter.order.anchor(card))
                editing = false
            }
            onShelfTargetHandled(shelfTarget)
        }
    }
    val category = filter.kind?.name.orEmpty()
    val matches = remember(cards, filter) { filter.select(cards) }
    val pending = cards.count { it.pending }
    val inboxLabel = when {
        pending > 0 -> stringResource(R.string.collection_pending_count, pending)
        canRevisitReveals -> stringResource(R.string.collection_revisit_reveals)
        else -> null
    }
    val openInbox = { onOpen(UnopenedCardsRoute) }
    val selectCard: (CollectionCard) -> Unit = { card ->
        anchors = anchors + (category to filter.order.anchor(card))
        onOpen(if (card.pending) RevealCardRoute(card.id) else CardDetailRoute(card.id))
    }

    CollectionPage(modifier, compactSpacing = 8.dp, fullTopPadding = 28.dp) { window ->
        val full = window.full
        Row(verticalAlignment = Alignment.CenterVertically) {
            CollectionTitle(stringResource(R.string.collection_title), full, Modifier.weight(1f))
            if (full && cards.isNotEmpty()) Caption(stringResource(R.string.collection_card_count, cards.size))
        }
        if (demoLabel != null) PleosTextLink(demoLabel, onDemoClick)
        if (cards.isEmpty()) {
            EmptyCollection(window) { onOpen(CollectionGuideRoute(it)) }
            return@CollectionPage
        }
        if (!full) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(stringResource(R.string.collection_card_count, cards.size), Modifier.weight(1f))
                if (inboxLabel != null) PleosTextLink(inboxLabel, openInbox)
            }
        }
        if (cards.size == 1) {
            FirstCard(cards.first(), window, selectCard, onOpen)
            return@CollectionPage
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            (listOf(null) + CardKind.entries).forEach { kind ->
                PleosChip(
                    label = kind?.label ?: stringResource(R.string.collection_all),
                    selected = filter.kind == kind,
                    onSelectedChange = { setFilter(filter.copy(kind = kind)) },
                )
            }
            if (full) {
                Spacer(Modifier.weight(1f))
                Caption(filter.order.label)
                Spacer(Modifier.weight(.65f))
                if (inboxLabel != null) PleosTextLink(inboxLabel, openInbox)
            }
            Spacer(Modifier.weight(1f))
            PleosButton(
                if (filter.placeConditions.isEmpty()) {
                    stringResource(R.string.collection_filter_sort)
                } else {
                    stringResource(R.string.collection_filter_sort_count, filter.placeConditions.size)
                },
                { editing = true },
                style = PleosButtonStyle.Secondary,
            )
        }
        val conditions = filter.placeConditions
        when {
            matches.isEmpty() -> NoMatches(
                filter,
                cards.size,
                full,
                { setFilter(filter.clearedPlaces()) },
                { editing = true },
                { onOpen(CollectionGuideRoute(it)) },
            )

            else -> {
                if (conditions.isNotEmpty()) {
                    Row {
                        Caption(conditions.joinToString(" · "), Modifier.weight(1f), accent = true)
                        Caption("${matches.size}장")
                    }
                }
                val columns = if (full) 4 else 2
                val index = filter.order.position(matches, anchors[category])
                val start = index / columns * columns
                val page = matches.drop(start).take(columns)
                val pair = cards.size == 2 && matches.size == 2 && matches.first().kind == CardKind.Place &&
                    matches.last().kind == CardKind.Safety && filter.order == CardOrder.FirstKept
                // Reserve the controls first. On tall windows the case can occupy everything in between.
                BoxWithConstraints(
                    if (window.tall) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                ) {
                    val caseHeight = if (window.tall) maxHeight else 0.dp
                    val caseModifier = Modifier.fillMaxWidth().heightIn(min = caseHeight)
                    val artBudget = if (window.tall) {
                        ((caseHeight - 166.dp) * MILESTONE_CARD_ASPECT_RATIO).coerceAtLeast(100.dp)
                    } else {
                        ((window.height - 283.dp) * MILESTONE_CARD_ASPECT_RATIO).coerceIn(
                            100.dp,
                            if (full) 188.dp else 184.dp,
                        )
                    }
                    when {
                        matches.size == 1 && maxWidth >= 620.dp ->
                            SingleMatch(page.single(), representativeId, selectCard, window, caseModifier)

                        full && pair && conditions.isEmpty() ->
                            TwoCardCollection(matches, representativeId, selectCard, window, caseHeight)

                        full && matches.size <= 2 ->
                            SmallCollectionShelf(page, representativeId, selectCard, window, caseHeight)

                        else -> ShelfRow(
                            page,
                            minOf(matches.size, columns),
                            artBudget,
                            full,
                            representativeId,
                            selectCard,
                            caseModifier,
                        )
                    }
                }
                ShelfControls(
                    start,
                    page.size,
                    matches.size,
                    columns,
                    onStep = { newIndex ->
                        anchors = anchors + (category to filter.order.anchor(matches[newIndex]))
                    },
                    onRepresentative = if (cards.any { it.revealedLevel != null }) {
                        { onOpen(RepresentativeCardRoute(representativeId)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
    if (editing) {
        CollectionFilterDialog(cards, filter, places = places, onClose = { editing = false }, onApply = {
            anchors = anchors - it.kind?.name.orEmpty()
            setFilter(it)
            editing = false
        })
    }
}

@Composable
private fun ShelfRow(
    cards: List<CollectionCard>,
    columns: Int,
    artWidth: Dp,
    full: Boolean,
    representativeId: String?,
    onSelect: (CollectionCard) -> Unit,
    modifier: Modifier,
) {
    VaultStage(modifier) {
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(
                start = 12.dp,
                end = 12.dp,
                top = 26.dp,
                bottom = if (full) 26.dp else 16.dp,
            ),
        ) {
            cards.forEach { card ->
                Column(
                    Modifier.weight(1f).testTag("collection-card-${card.id}")
                        .customClickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.collection_open_named_card, card.publicTitle),
                        ) {
                            onSelect(card)
                        }.padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DisplayedArt(card.face(), artWidth)
                    Column(
                        Modifier.widthIn(max = artWidth + 12.dp).fillMaxWidth().padding(horizontal = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(if (full) 6.dp else 4.dp),
                    ) {
                        Spacer(Modifier.height(if (full) 8.dp else 4.dp))
                        SectionTitle(card.publicTitle)
                        CardStatus(card, representativeId)
                    }
                }
            }
            repeat(columns - cards.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SingleMatch(
    card: CollectionCard,
    representativeId: String?,
    onSelect: (CollectionCard) -> Unit,
    window: CollectionWindow,
    modifier: Modifier,
) {
    VaultStage(modifier) {
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(if (window.full) 40.dp else 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (window.full) 40.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1.15f), contentAlignment = Alignment.Center) {
                DisplayedArt(
                    card.face(),
                    window.printWidth(260.dp, 208.dp),
                    Modifier.testTag("collection-card-${card.id}").customClickable(role = Role.Button) {
                        onSelect(card)
                    },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Caption(card.kind.label, accent = true)
                CollectionTitle(card.publicTitle, window.full)
                CardStatus(card, representativeId)
                Caption(card.memorySummary)
                PleosButton(
                    if (card.pending) "열어보기" else stringResource(R.string.collection_open_card),
                    { onSelect(card) },
                    showArrow = true,
                )
            }
        }
    }
}

/** D01 gives the place print and its memory a larger case beside the first safety print. */
@Composable
private fun TwoCardCollection(
    cards: List<CollectionCard>,
    representativeId: String?,
    onSelect: (CollectionCard) -> Unit,
    window: CollectionWindow,
    caseHeight: Dp,
) {
    val place = cards.first { it.kind == CardKind.Place }
    val safety = cards.first { it.kind == CardKind.Safety }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        VaultStage(Modifier.weight(2f).heightIn(min = caseHeight)) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CardStatus(place, representativeId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        DisplayedArt(place.face(), window.printWidth(244.dp, 244.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionTitle(place.publicTitle)
                        Caption(place.place?.displayName.orEmpty())
                        Caption(stringResource(R.string.collection_first_collected))
                        SectionTitle(place.firstDate.displayDate())
                        Caption(place.memorySummary, accent = true)
                        PleosButton(
                            if (place.pending) "열어보기" else stringResource(R.string.collection_open_card),
                            {
                                onSelect(place)
                            },
                            Modifier.fillMaxWidth().testTag("collection-card-${place.id}"),
                            showArrow = true,
                        )
                    }
                }
            }
        }
        VaultStage(Modifier.weight(1f).heightIn(min = caseHeight), highlighted = safety.id == representativeId) {
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DisplayedArt(
                    safety.face(),
                    window.printWidth(142.dp, 142.dp),
                    Modifier.align(Alignment.CenterHorizontally),
                )
                SectionTitle(safety.publicTitle)
                Caption(safety.memorySummary)
                PleosButton(
                    if (safety.pending) "열어보기" else stringResource(R.string.collection_open_card),
                    {
                        onSelect(safety)
                    },
                    Modifier.fillMaxWidth().testTag("collection-card-${safety.id}"),
                    style = PleosButtonStyle.Secondary,
                )
            }
        }
    }
}

@Composable
private fun SmallCollectionShelf(
    cards: List<CollectionCard>,
    representativeId: String?,
    onSelect: (CollectionCard) -> Unit,
    window: CollectionWindow,
    caseHeight: Dp,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        cards.forEach { card ->
            VaultStage(
                Modifier.weight(
                    1f,
                ).heightIn(min = caseHeight).testTag("collection-card-${card.id}").customClickable(role = Role.Button) {
                    onSelect(card)
                },
            ) {
                val width = window.printWidth(224.dp, 184.dp)
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = width + 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DisplayedArt(card.face(), width)
                    SectionTitle(card.publicTitle)
                    CardStatus(card, representativeId)
                    Caption(card.memorySummary)
                }
            }
        }
    }
}

@Composable
private fun CardStatus(card: CollectionCard, representativeId: String?) {
    val featured = stringResource(R.string.collection_featured)
    val status = buildList {
        if (card.id == representativeId && card.revealedLevel != null) add(featured)
        if (card.pending) add(card.revealLabel)
    }
    Caption(
        if (status.isEmpty()) "${card.firstDate.shortDate()}부터" else status.joinToString(" · "),
        accent = status.isNotEmpty(),
    )
}

@Composable
private fun ShelfControls(
    start: Int,
    count: Int,
    total: Int,
    columns: Int,
    onStep: (Int) -> Unit,
    onRepresentative: (() -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 620.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (total > columns) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (wide) {
                        Box(Modifier.weight(1f)) {
                            if (onRepresentative != null) {
                                PleosTextLink(
                                    stringResource(R.string.card_detail_change_featured),
                                    onRepresentative,
                                )
                            }
                        }
                    }
                    if (!wide) {
                        PleosButton("이전", {
                            onStep(start - columns)
                        }, enabled = start > 0, style = PleosButtonStyle.Secondary)
                    }
                    Box(if (wide) Modifier else Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Caption("${start + 1}–${start + count} / $total")
                    }
                    if (wide) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                            PleosButton("이전", {
                                onStep(start - columns)
                            }, enabled = start > 0, style = PleosButtonStyle.Secondary)
                        }
                    }
                    PleosButton("다음", {
                        onStep(start + columns)
                    }, enabled = start + count < total, style = PleosButtonStyle.Secondary)
                }
            }
            if ((!wide || total <= columns) &&
                onRepresentative != null
            ) {
                PleosTextLink(stringResource(R.string.card_detail_change_featured), onRepresentative)
            }
        }
    }
}

@Composable
private fun ColumnScope.FirstCard(
    card: CollectionCard,
    window: CollectionWindow,
    onSelect: (CollectionCard) -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    CardExhibit(
        window,
        if (window.full) 262.dp else 210.dp,
        "처음 간직한 한 장",
        Modifier.testTag("collection-card-${card.id}").customClickable(role = Role.Button) { onSelect(card) },
        print = { MilestoneCard(card.face(), it) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTitle(card.publicTitle)
            Caption("처음 간직한 날 · ${card.firstDate.displayDate()}")
            Caption(card.memorySummary, accent = true)
            PleosButton(if (card.pending) "카드 열기" else stringResource(R.string.collection_open_card), {
                onSelect(card)
            }, Modifier.fillMaxWidth(), showArrow = true)
            if (card.revealedLevel != null) PleosTextLink("홈에 전시하기", { onOpen(RepresentativeCardRoute(card.id)) })
        }
    }
}

@Composable
private fun NoMatches(
    filter: CollectionFilter,
    owned: Int,
    full: Boolean,
    onReset: () -> Unit,
    onEdit: () -> Unit,
    onGuide: (CardKind) -> Unit,
) {
    CollectionColumns(full, first = {
        PleosInfoPanel(Modifier.fillMaxWidth()) {
            SectionTitle("아직 이 조건의 카드는 없어요")
            Caption("간직한 ${owned}장의 카드는 그대로 있어요.")
            if (filter.placeConditions.isNotEmpty()) Caption(filter.placeConditions.joinToString(" · "), accent = true)
        }
    }, second = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("다른 기억을 찾아볼까요?")
            if (filter.placeConditions.isNotEmpty()) {
                PleosButton("지역·유형 초기화", onReset, Modifier.fillMaxWidth(), showArrow = true)
            } else {
                filter.kind?.let { kind -> PleosTextLink("어떻게 남기나요?", { onGuide(kind) }) }
            }
            PleosButton("필터 바꾸기", onEdit, Modifier.fillMaxWidth(), style = PleosButtonStyle.Secondary)
        }
    })
}
