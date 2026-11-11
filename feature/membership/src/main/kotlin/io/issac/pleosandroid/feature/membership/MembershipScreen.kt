package io.issac.pleosandroid.feature.membership

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.issac.pleosandroid.core.designsystem.component.PleosBackLink
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosButtonStyle
import io.issac.pleosandroid.core.designsystem.component.PleosDialog
import io.issac.pleosandroid.core.designsystem.component.PleosInfoPanel
import io.issac.pleosandroid.core.designsystem.component.PleosScreen
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.core.domain.membership.Membership
import io.issac.pleosandroid.core.domain.membership.MembershipAction
import io.issac.pleosandroid.core.domain.membership.MembershipStatus
import io.issac.pleosandroid.core.domain.membership.MembershipTerm
import java.time.format.DateTimeFormatter
import java.util.Locale

private val periodDate = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREA).withZone(MembershipTerm.zone)
private const val MONTHLY_PRICE = "월 2,900원"

@Composable
internal fun MembershipDestination(
    managing: Boolean,
    onBack: () -> Unit,
    onManage: () -> Unit,
    onShop: () -> Unit,
    onRecordingSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MembershipViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MembershipScreen(
        state = state,
        managing = managing,
        onBack = onBack,
        onManage = onManage,
        onShop = onShop,
        onRecordingSettings = onRecordingSettings,
        confirm = viewModel::confirm,
        retry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
internal fun MembershipScreen(
    state: MembershipUiState,
    managing: Boolean,
    onBack: () -> Unit,
    onManage: () -> Unit,
    onShop: () -> Unit,
    onRecordingSettings: () -> Unit,
    confirm: (MembershipAction, Membership) -> Unit,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PleosTheme.colors
    val membership = (state.load as? MembershipLoad.Ready)?.membership
    var pending by remember(membership, state.parked) { mutableStateOf<MembershipAction?>(null) }
    PleosScreen(if (managing) "구독 관리" else "상점", modifier) {
        if (managing || membership?.demo == true) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (managing) PleosBackLink("돌아가기", onBack)
                if (membership?.demo == true) {
                    Text("체험 모드 · 내 구독과 별도로 이용해요.", style = PleosTextStyles.meta, color = colors.actionPrimary)
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val details: @Composable (Modifier) -> Unit = { panel ->
                PleosInfoPanel(panel) {
                    when (state.load) {
                        MembershipLoad.Loading -> Text(
                            "구독을 확인하고 있어요",
                            style = PleosTextStyles.body,
                            color = colors.textSecondary,
                        )

                        MembershipLoad.Failed -> {
                            Text("구독 정보를 불러올 수 없어요.", style = PleosTextStyles.body, color = colors.textPrimary)
                            PleosButton("다시 시도", retry, Modifier.fillMaxWidth())
                        }

                        is MembershipLoad.Ready -> MembershipDetails(
                            state.load.membership,
                            managing,
                            state.busy,
                            state.parked,
                            onManage,
                            onShop,
                            onRequest = { pending = it },
                        )
                    }
                    state.error?.let {
                        Text(
                            it,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = colors.statusAttention,
                            style = PleosTextStyles.meta,
                        )
                    }
                }
            }
            if (maxWidth >= PleosLayout.twoPaneMinWidth) {
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(PleosLayout.columnGap),
                ) {
                    PlusPass(Modifier.weight(1f).fillMaxHeight())
                    details(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(PleosLayout.sectionGap)) {
                    PlusPass()
                    details(Modifier.fillMaxWidth())
                }
            }
        }
        if (membership != null && !membership.demo && !state.recordingEnabled) {
            Text("주행 포인트를 적립하려면 여정 기록 저장을 켜 주세요.", style = PleosTextStyles.body, color = colors.textSecondary)
            PleosTextLink("여정 기록 설정", onRecordingSettings, enabled = state.parked)
        }
    }

    val action = pending
    if (action != null && membership != null && state.parked) {
        val ending = membership.periodEnd?.let(periodDate::format).orEmpty()
        val (title, message, label) = when (action) {
            MembershipAction.Subscribe -> Triple(
                "PLUS를 구독할까요?",
                "${MONTHLY_PRICE}에 자동 갱신돼요. 지금은 모의 결제라 실제 청구는 없어요.",
                "구독 시작",
            )

            MembershipAction.CancelRenewal -> Triple(
                "구독을 해지할까요?",
                "${ending}까지 1.5배로 적립돼요. 그 뒤에는 기본 적립으로 돌아가요.",
                "구독 해지",
            )

            MembershipAction.ResumeRenewal -> Triple(
                "자동 갱신을 다시 켤까요?",
                "${ending}에 다음 이용 기간이 시작돼요. 추가 청구는 없어요.",
                "자동 갱신 재개",
            )
        }
        PleosDialog(
            title,
            message,
            label,
            onConfirm = {
                pending = null
                confirm(action, membership)
            },
            onDismissRequest = { pending = null },
            dismissLabel = "닫기",
            confirmEnabled = !state.busy,
        )
    }
}

@Composable
private fun MembershipDetails(
    membership: Membership,
    managing: Boolean,
    busy: Boolean,
    parked: Boolean,
    onManage: () -> Unit,
    onShop: () -> Unit,
    onRequest: (MembershipAction) -> Unit,
) {
    val colors = PleosTheme.colors
    val status = membership.status
    val heading = when (status) {
        MembershipStatus.Renewing -> "구독 중"
        MembershipStatus.Ending -> "해지 예약됨"
        MembershipStatus.Expired -> "구독 종료"
        MembershipStatus.Inactive -> if (managing) "이용 중인 구독이 없어요" else "월간 구독"
    }
    Text(
        heading,
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
        style = PleosTextStyles.section,
        color = colors.textPrimary,
    )
    Text(MONTHLY_PRICE, style = PleosTextStyles.body, color = colors.textPrimary)
    val date = membership.periodEnd?.let(periodDate::format)
    val description = when (status) {
        MembershipStatus.Renewing -> "다음 갱신 · $date"
        MembershipStatus.Ending -> "혜택 종료 · $date"
        MembershipStatus.Expired -> "혜택이 ${date}에 종료됐어요."
        MembershipStatus.Inactive -> "평소 100 P를 받는 주행이라면 150 P가 적립돼요."
    }
    Text(description, style = PleosTextStyles.body, color = colors.textSecondary)
    val (label, activate) = when {
        managing && status == MembershipStatus.Renewing -> "구독 해지" to { onRequest(MembershipAction.CancelRenewal) }
        managing && status == MembershipStatus.Ending -> "자동 갱신 재개" to { onRequest(MembershipAction.ResumeRenewal) }
        managing -> "상점에서 보기" to onShop
        membership.hasBenefit -> "구독 관리" to onManage
        else -> "PLUS 구독하기" to { onRequest(MembershipAction.Subscribe) }
    }
    PleosButton(
        when {
            busy -> "처리 중…"
            !parked -> "주차 후 이용"
            else -> label
        },
        onClick = activate,
        modifier = Modifier.fillMaxWidth(),
        enabled = parked && !busy,
        style = if (managing &&
            status == MembershipStatus.Renewing
        ) {
            PleosButtonStyle.Secondary
        } else {
            PleosButtonStyle.Primary
        },
    )
    if (!membership.hasBenefit && !managing) {
        Text(
            "구독 중 달린 거리부터 적용돼요. 2km 이상 달리고 안전점수가 나오면 적립돼요.",
            style = PleosTextStyles.meta,
            color = colors.textSecondary,
        )
    }
}
