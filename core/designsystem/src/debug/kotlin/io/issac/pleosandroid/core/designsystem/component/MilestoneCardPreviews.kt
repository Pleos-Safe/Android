package io.issac.pleosandroid.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.issac.pleosandroid.core.designsystem.R
import io.issac.pleosandroid.core.designsystem.theme.PleosTheme

@Preview(name = "Milestone card • place front")
@Composable
private fun MilestonePlaceFrontPreview() {
    PleosTheme {
        MilestoneCard(
            face = MilestoneCardFace.Front(
                appearance = MilestoneCardAppearance.PlaceLevel1,
                artwork = painterResource(R.drawable.milestone_preview_haeundae),
                contextLabel = "부산 · 해운대",
                title = "해운대의 파도",
                subtitle = "부산의 여름을 간직하다",
                seriesLabel = "MILESTONE / 001",
                categoryLabel = "장소의 기억",
            ),
            modifier = Modifier.width(240.dp),
        )
    }
}

@Preview(name = "Milestone card • common back")
@Composable
private fun MilestoneCommonBackPreview() {
    PleosTheme {
        MilestoneCard(
            face = MilestoneCardFace.CommonBack,
            modifier = Modifier.width(240.dp),
        )
    }
}
