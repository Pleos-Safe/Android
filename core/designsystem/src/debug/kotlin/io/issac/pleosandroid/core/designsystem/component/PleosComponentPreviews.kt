package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.theme.PleosSpacing
import io.issac.pleosandroid.core.designsystem.theme.PleosTextStyles
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Composable
private fun Samples(maxWidth: Dp = 520.dp, content: @Composable ColumnScope.() -> Unit) {
    PleosTheme {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth()
                .background(PleosTheme.colors.background).padding(PleosSpacing.space16),
            verticalArrangement = Arrangement.spacedBy(PleosSpacing.space12),
            content = content,
        )
    }
}

@Preview(name = "버튼 · 링크", group = "Actions", widthDp = 384)
@Composable
internal fun ButtonPreview() = Samples(maxWidth = 384.dp) {
    PleosButton("목적지로 설정", {}, Modifier.fillMaxWidth(), showArrow = true)
    PleosButton("장소 자세히", {}, Modifier.fillMaxWidth(), style = PleosButtonStyle.Secondary, showArrow = true)
    PleosButton("기록 삭제", {}, Modifier.fillMaxWidth(), style = PleosButtonStyle.Danger, showArrow = true)
    PleosButton("위치 확인 중", {}, Modifier.fillMaxWidth(), enabled = false, showArrow = true)
    PleosTextLink("나중에 확인", {})
}

@Preview(name = "필터 · 입력창", group = "Input", widthDp = 400)
@Composable
internal fun InputPreview() = Samples(maxWidth = 400.dp) {
    val filters = listOf("전체", "장소", "운전습관")
    var selected by remember { mutableStateOf(filters.first()) }
    Row(horizontalArrangement = Arrangement.spacedBy(PleosSpacing.space8)) {
        filters.forEach { label -> PleosChip(label, selected == label, { selected = label }) }
    }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("광안리") }
    PleosTextField(name, { name = it }, "장소 이름", Modifier.fillMaxWidth(), placeholder = "장소 이름")
    PleosSearchField(query, { query = it }, {}, Modifier.fillMaxWidth())
}

@Preview(name = "전체 헤더", group = "Navigation", widthDp = 1216)
@Preview(name = "분할 헤더", group = "Navigation", widthDp = 400)
@Composable
internal fun HeaderPreview() = PleosTheme {
    var tab by remember { mutableStateOf(PleosTab.Travel) }
    PleosHeader(tab, { tab = it }, {}, restricted = false, Modifier.background(PleosTheme.colors.shellStart))
}

@Preview(name = "조작 제한 헤더", group = "Navigation", widthDp = 400)
@Composable
internal fun RestrictedHeaderPreview() = PleosTheme {
    PleosHeader(PleosTab.Travel, {}, {}, restricted = true, Modifier.background(PleosTheme.colors.shellStart))
}

@Preview(name = "분할 하단 탭", group = "Navigation", widthDp = 400)
@Composable
internal fun BottomTabPreview() = PleosTheme {
    var tab by remember { mutableStateOf(PleosTab.Collection) }
    PleosBottomTabs(tab, { tab = it }, restricted = false)
}

@Preview(name = "장소 핀 · 군집 · 내 위치 미확인", group = "Map", widthDp = 520)
@Composable
internal fun MapPreview() = Samples {
    var selected by remember { mutableStateOf("광안리 해변") }
    PleosPlaceMap(
        modifier = Modifier.fillMaxWidth().height(312.dp),
        caption = "부산 · 지도",
        onZoomIn = {},
        onZoomOut = {},
    ) {
        Image(
            painterResource(R.drawable.pleos_busan_coast),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
        )
        PleosMapPin(
            "해운대 해변",
            selected == "해운대 해변",
            { selected = "해운대 해변" },
            Modifier.align(BiasAlignment(.52f, -.49f)),
            showLabel = true,
        )
        PleosMapPin(
            "광안리 해변",
            selected == "광안리 해변",
            { selected = "광안리 해변" },
            Modifier.align(Alignment.Center),
            showLabel = true,
        )
        PleosMapCluster(3, {}, Modifier.align(BiasAlignment(-.68f, .3f)))
    }
}

@Preview(name = "확인 다이얼로그", group = "Overlays", widthDp = 480, heightDp = 320)
@Composable
internal fun DialogPreview() = Samples {
    var open by remember { mutableStateOf(true) }
    PleosButton("다이얼로그 열기", { open = true })
    if (open) {
        PleosDialog(
            title = "방문을 남길까요?",
            message = "실제로 다녀온 곳을 여정에 남겨요.",
            confirmLabel = "방문했어요",
            onConfirm = { open = false },
            onDismissRequest = { open = false },
            dismissLabel = "취소",
        )
    }
}

@Preview(name = "장소 정보 시트", group = "Overlays", widthDp = 480, heightDp = 360)
@Composable
internal fun SheetPreview() = Samples {
    var open by remember { mutableStateOf(true) }
    PleosButton("시트 열기", { open = true })
    if (open) {
        PleosBottomSheet("광안리 해변", { open = false }) {
            Text("부산 수영구 · 전망·명소", style = PleosTextStyles.body, color = PleosTheme.colors.textSecondary)
            PleosButton("닫기", { open = false }, Modifier.fillMaxWidth(), style = PleosButtonStyle.Secondary)
        }
    }
}
