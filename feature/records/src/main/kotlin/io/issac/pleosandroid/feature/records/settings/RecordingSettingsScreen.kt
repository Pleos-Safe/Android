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
                    RecordHeading("저장할 정보")
                    RecordBody("주행 시간과 거리, 급가속·급감속 횟수를 이 기기에 암호화해 보관해요.")
                    RecordCaption("속도 이력과 이동 경로는 저장하지 않아요.")
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
                    RecordBody("위치 권한 없이도 운전습관을 기록해요. 위치를 허용하면 목적지나 경유지로 등록한 여행지에 주차한 뒤 방문팩을 받을 수 있어요.")
                    RecordCaption("방문을 확인한 장소와 시각을 함께 저장해요. 기록을 끄면 진행 중인 여정의 저장이 중단돼요.")
                    if (state.demo) RecordCaption("실제 주행을 기록하려면 컬렉션에서 체험 모드를 종료해 주세요.", accent = true)
                }
            },
        )
        RecordingStatus(state, onIntent)
    }
}
