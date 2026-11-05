package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Preview(name = "Milestone card • place front", group = "Basic faces")
@Composable
private fun MilestonePlaceFrontPreview() {
    PleosTheme {
        MilestoneCard(
            face = previewFace(MilestoneCardAppearance.PlaceLevel1),
            modifier = Modifier.width(240.dp),
        )
    }
}

@Preview(name = "Milestone card • common back", group = "Basic faces")
@Composable
private fun MilestoneCommonBackPreview() {
    PleosTheme {
        MilestoneCard(
            face = MilestoneCardFace.CommonBack,
            modifier = Modifier.width(240.dp),
        )
    }
}

@Preview(name = "Milestone card • all front variants and back", group = "Variants")
@Composable
private fun MilestoneVariantGalleryPreview() {
    PleosTheme {
        Surface {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MilestoneCardAppearance.entries.groupBy { it.isSafety }.values.forEach { family ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        family.forEach { appearance ->
                            MilestoneCard(
                                face = previewFace(appearance),
                                modifier = Modifier.width(160.dp),
                            )
                        }
                    }
                }
                MilestoneCard(MilestoneCardFace.CommonBack, Modifier.width(160.dp))
            }
        }
    }
}

@Preview(name = "Milestone card • compact with larger text", group = "Variants")
@Composable
private fun MilestoneCompactLongTitlePreview() {
    PleosTheme {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
            MilestoneCard(
                face = previewFace(MilestoneCardAppearance.PlaceLevel3).copy(
                    contextLabel = "부산광역시 해운대구 · 해운대",
                    title = "해운대에서 다시 만난 바다의 파도를 기억하다",
                    subtitle = "부산의 여름과 오래 간직하고 싶은 여정의 기억",
                ),
                modifier = Modifier.width(112.dp),
            )
        }
    }
}

@Composable
private fun previewFace(appearance: MilestoneCardAppearance): MilestoneCardFace.Front =
    if (appearance.isSafety) {
        MilestoneCardFace.Front(
            appearance = appearance,
            artwork = painterResource(R.drawable.milestone_art_safety),
            contextLabel = "운전습관 · 귀가",
            title = "안전한 귀가",
            subtitle = "여정을 차분히 마친 기록",
            seriesLabel = "SAFETY / SMOOTH / 001",
        )
    } else {
        MilestoneCardFace.Front(
            appearance = appearance,
            artwork = painterResource(R.drawable.milestone_art_haeundae),
            contextLabel = "부산 · 해운대",
            title = "해운대의 파도",
            subtitle = "부산의 여름을 간직하다",
            seriesLabel = "MILESTONE / 001",
            categoryLabel = "장소의 기억",
        )
    }
