package io.issac.pleosandroid.feature.badges.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosChip
import io.issac.pleosandroid.core.domain.cards.AlbumSnapshot
import io.issac.pleosandroid.core.domain.cards.CardCopy
import io.issac.pleosandroid.core.domain.cards.CardRules
import io.issac.pleosandroid.core.domain.cards.CopyStatus
import io.issac.pleosandroid.core.domain.cards.ForgeOdds
import io.issac.pleosandroid.core.domain.cards.ForgeOutcome
import io.issac.pleosandroid.feature.badges.component.Caption
import io.issac.pleosandroid.feature.badges.component.SectionTitle
import java.util.UUID

internal val ForgeOutcome.label: String get() = when (this) {
    ForgeOutcome.Success -> "강화 성공"
    ForgeOutcome.GreatSuccess -> "대성공"
    ForgeOutcome.Hold -> "강화 유지"
    ForgeOutcome.Drop -> "강화 하락"
    ForgeOutcome.Destroyed -> "카드 파괴"
    ForgeOutcome.Restored -> "복구 완료"
}

@Composable
internal fun CardWorkshop(
    card: CardCopy,
    album: AlbumSnapshot,
    busy: Boolean,
    send: (AlbumIntent) -> Unit,
    onBack: () -> Unit,
    inCollection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val receipt = album.pending[card.id]
    // A pending receipt is durable; only this visit to the workshop owns the animation it requested.
    val requestId = rememberSaveable(card.id, card.revision) { UUID.randomUUID().toString() }
    var playbackId by rememberSaveable(card.id) { mutableStateOf<String?>(null) }
    var picking by rememberSaveable(card.id) { mutableStateOf(false) }
    var materialId by rememberSaveable(card.id) { mutableStateOf<String?>(null) }
    var exitAfterResult by rememberSaveable(card.id) { mutableStateOf(false) }
    LaunchedEffect(receipt?.id, exitAfterResult) {
        if (exitAfterResult && receipt == null) {
            exitAfterResult = false
            inCollection(card.id)
        }
    }
    val trace = card.status == CopyStatus.Trace
    ForgePresentation(card, receipt, playbackId, { finished ->
        if (playbackId == finished) playbackId = null
    }, onBack, modifier) { playing, art ->
        val title = when {
            playing -> if (receipt?.outcome == ForgeOutcome.Restored) "흔적 복구 중" else "강화 중"
            receipt != null -> receipt.outcome.label
            trace -> "흔적 복구"
            else -> "카드 강화"
        }
        ExhibitScreen(
            title,
            onBack,
            Modifier.fillMaxSize(),
            note = points(album.balanceHalfPoints),
            art = art,
        ) {
            Caption("${card.print.title} · ${card.grade}" + if (card.variant) " · 변종" else "", accent = true)
            when {
                receipt != null && playing -> {
                    val message = when (receipt.outcome) {
                        ForgeOutcome.Restored -> "흔적이 빛을 되찾고 있어요"
                        else -> "+${receipt.before} · 강화 중"
                    }
                    SectionTitle(message)
                    Caption("잠시 후 결과를 확인할 수 있어요.")
                }

                receipt != null -> {
                    SectionTitle(
                        if (receipt.outcome == ForgeOutcome.Destroyed) {
                            "+${receipt.before} → 흔적 +${receipt.after}"
                        } else {
                            "+${receipt.before} → +${receipt.after}"
                        },
                    )
                    BodyText("소모 포인트   ${points(receipt.costHalfPoints)}")
                    BodyText("남은 포인트   ${points(album.balanceHalfPoints)}")
                    if (receipt.outcome == ForgeOutcome.Destroyed) Caption("같은 색상의 동일 카드 +0 한 장으로 복구할 수 있어요.")
                    PleosButton(
                        if (trace) "복구 방법 보기" else "다음 강화 보기",
                        { send(AlbumIntent.Acknowledge(receipt.id)) },
                        Modifier.fillMaxWidth().testTag("forge-next"),
                        enabled = !busy,
                        showArrow = true,
                    )
                    PleosButton(
                        "컬렉션에서 보기",
                        {
                            exitAfterResult = true
                            send(AlbumIntent.Acknowledge(receipt.id))
                        },
                        Modifier.fillMaxWidth(),
                        enabled = !busy,
                        style = PleosButtonStyle.Secondary,
                    )
                }

                trace -> {
                    val materials = album.materialsFor(card)
                    SectionTitle("복구 수치  +${card.level}")
                    BodyText("같은 색상의 동일 카드 +0 한 장을 사용하면 원래 카드를 확정 복구해요.")
                    Caption("최고 강화 +${card.highest} · 복구 비용 0 P")
                    PleosButton(
                        "복구 재료 선택 · ${materials.size}장",
                        { picking = true },
                        Modifier.fillMaxWidth(),
                        enabled =
                            !busy && materials.isNotEmpty(),
                    )
                    if (materials.isEmpty()) Caption("사용할 수 있는 +0 사본이 없어요.")
                }

                else -> {
                    val cost = CardRules.cost(card.grade, card.level)
                    val odds = CardRules.odds(card.level)
                    SectionTitle("+${card.level} → +${card.level + 1}")
                    BodyText("1회 비용   ${points(cost)}")
                    OddsBar(odds)
                    if (odds.destroy > 0) Caption("파괴 시 +${card.level - 5}의 흔적이 남아요.")
                    PleosButton(
                        "${points(cost)}로 강화하기",
                        {
                            playbackId = requestId
                            send(AlbumIntent.Enhance(card, requestId))
                        },
                        Modifier.fillMaxWidth().testTag("forge-attempt"),
                        enabled = !busy && album.balanceHalfPoints >= cost,
                        showArrow = true,
                    )
                    Caption(if (album.balanceHalfPoints < cost) "강화 포인트가 부족해요." else "모든 결과에서 포인트를 한 번 소모해요.")
                }
            }
        }
    }
    if (picking && trace && receipt == null) {
        val materials = album.materialsFor(card)
        val material = materials.find { it.id == materialId }
        AlertDialog(
            onDismissRequest = {
                picking = false
                materialId = null
            },
            title = { Text("복구 재료 선택") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(materials, key = { it.id }) { item ->
                        PleosChip(
                            "${item.print.title} +0 · ${dateOf(item.acquiredAt)} · ${item.id.takeLast(6)}" +
                                if (item.id == album.featuredId) " · 대표" else "",
                            materialId == item.id,
                            {
                                materialId = item.id
                            },
                        )
                    }
                    if (material != null) item { BodyText("선택한 사본 한 장을 소모해 +${card.level}로 복구해요.") }
                }
            },
            confirmButton = {
                TextButton(enabled = material != null && !busy, onClick = {
                    material?.let {
                        playbackId = requestId
                        send(AlbumIntent.Restore(card, it.id, requestId))
                    }
                    picking = false
                    materialId = null
                }) { Text("이 사본으로 복구") }
            },
            dismissButton = {
                TextButton(onClick = {
                    picking = false
                    materialId = null
                }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun OddsBar(odds: ForgeOdds) {
    val values =
        listOf("성공" to odds.success, "대성공" to odds.great, "유지" to odds.hold, "하락" to odds.drop, "파괴" to odds.destroy)
    val colors = listOf(
        androidx.compose.ui.graphics.Color(0xFF9FF4E5),
        androidx.compose.ui.graphics.Color(0xFFC4A9F8),
        androidx.compose.ui.graphics.Color(0xFF90A0BA),
        androidx.compose.ui.graphics.Color(0xFFE7C391),
        androidx.compose.ui.graphics.Color(0xFFECA2B3),
    )
    BodyText("이번 강화 확률")
    Row(Modifier.fillMaxWidth().height(6.dp)) {
        values.forEachIndexed { i, (_, percent) ->
            if (percent >
                0
            ) {
                Spacer(Modifier.weight(percent.toFloat()).fillMaxHeight().then(Modifier.background(colors[i])))
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { (name, chance) -> Caption("$name $chance%") }
    }
}
