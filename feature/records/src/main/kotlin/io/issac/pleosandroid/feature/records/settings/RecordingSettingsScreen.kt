package io.issac.pleosandroid.feature.records.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.theme.PleosLayout
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme
import io.issac.pleosandroid.feature.records.component.QuietPlate
import io.issac.pleosandroid.feature.records.component.RecordBody
import io.issac.pleosandroid.feature.records.component.RecordCaption
import io.issac.pleosandroid.feature.records.component.RecordColumns
import io.issac.pleosandroid.feature.records.component.RecordDivider
import io.issac.pleosandroid.feature.records.component.RecordHeading
import io.issac.pleosandroid.feature.records.component.RecordIcon
import io.issac.pleosandroid.feature.records.component.RecordSymbol
import io.issac.pleosandroid.feature.records.component.RecordsPage
import io.issac.pleosandroid.feature.records.history.RecordingStatus
import io.issac.pleosandroid.feature.records.history.RecordsIntent
import io.issac.pleosandroid.feature.records.history.RecordsState

@Composable
internal fun RecordingSettingsScreen(
    state: RecordsState,
    onIntent: (RecordsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PleosTheme.colors
    val scale = PleosTextStyles.scale
    RecordsPage("여정 기록 저장", modifier, onBack = onBack) { wide ->
        RecordColumns(
            wide,
            leading = {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp * PleosTextStyles.scale)) {
                    RecordIcon(RecordSymbol.Bookmark)
                    RecordHeading("나의 기록 방식")
                    RecordBody("일상의 여정을 남기고,\n주차 후 운전습관을 돌아봐요.")
                }
            },
            trailing = {
                QuietPlate {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PleosLayout.itemGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                            RecordHeading("여정 기록 저장")
                            RecordCaption(if (state.enabled) "다음 출발부터 자동으로 기록해요." else "지금은 새 여정을 저장하지 않아요.")
                        }
                        Box(Modifier.size(64.dp * scale, 48.dp * scale), contentAlignment = Alignment.Center) {
                            Switch(
                                checked = state.enabled,
                                onCheckedChange = { onIntent(RecordsIntent.SetEnabled(it)) },
                                enabled = !state.loading && !state.busy && (!state.demo || state.enabled),
                                modifier = Modifier.scale(scale).semantics { contentDescription = "여정 기록 저장" },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = colors.actionPrimary,
                                    checkedThumbColor = colors.textOnAction,
                                    uncheckedTrackColor = colors.surface,
                                    uncheckedThumbColor = colors.textSecondary,
                                    uncheckedBorderColor = colors.borderControl,
                                ),
                            )
                        }
                    }
                    RecordDivider()
                    RecordBody("차량의 속도·기어 정보로 운전습관을 기록해요. 위치를 허용하면 주차 후 장소 체류를 확인해 카드도 모을 수 있어요.")
                    RecordCaption(
                        "여정 시간, 관찰 거리, 가속·감속 횟수와 데이터 상태를 이 기기에 암호화해 보관해요. 확인된 장소와 방문 시간을 함께 보관하며, 원본 속도 이력과 위치 궤적은 저장하지 않아요.",
                    )
                    RecordCaption("위치를 허용하지 않아도 운전습관은 기록해요. 위치 정확도나 체류 시간이 부족하면 방문 카드를 지급하지 않아요.")
                    RecordCaption("끄면 진행 중인 기록은 남기지 않아요. 이미 저장한 기록과 카드는 유지돼요. 다시 켜도 이전 여정은 가져오지 않아요.")
                    if (state.demo) RecordCaption("데모 모드에서는 실제 주행을 기록하지 않아요. 컬렉션에서 데모를 끈 뒤 설정할 수 있어요.", accent = true)
                }
            },
        )
        RecordingStatus(state, onIntent)
    }
}
