package io.issac.pleosandroid.core.cardui.preview

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.issac.pleosandroid.core.cardui.component.BodyText
import io.issac.pleosandroid.core.cardui.component.ExhibitScreen
import io.issac.pleosandroid.core.cardui.component.SectionTitle
import io.issac.pleosandroid.core.designsystem.component.PleosButton
import io.issac.pleosandroid.core.designsystem.component.PleosTextLink

@Composable
fun PreviewStage(
    title: String,
    detail: String,
    done: Boolean,
    replay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null,
    art: @Composable (Modifier) -> Unit,
) {
    ExhibitScreen("연출 미리보기", onBack, modifier, art = art) {
        SectionTitle(title)
        BodyText(detail)
        if (done) {
            PleosButton("다시 재생", replay, Modifier.fillMaxWidth().testTag("motion-replay"), showArrow = true)
        } else if (onSkip != null) {
            PleosButton("연출 건너뛰기", onSkip, Modifier.fillMaxWidth().testTag("motion-skip"))
        }
        PleosTextLink("연출 목록", onBack)
    }
}
