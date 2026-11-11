package io.issac.pleosandroid.feature.forge.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.issac.pleosandroid.core.cardui.component.label
import io.issac.pleosandroid.core.cardui.preview.PreviewStage
import io.issac.pleosandroid.core.cardui.preview.forgePreviewDetail
import io.issac.pleosandroid.core.cardui.preview.previewForgeCard
import io.issac.pleosandroid.core.domain.cards.ForgeReceipt
import io.issac.pleosandroid.core.navigation.ForgeMotionRoute
import io.issac.pleosandroid.feature.forge.motion.ForgePresentation
import java.time.Instant

@Composable
internal fun ForgeMotionPlayer(scene: ForgeMotionRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var run by rememberSaveable(scene) { mutableIntStateOf(0) }
    val replay: () -> Unit = { run++ }
    key(scene, run) {
        val card = remember(scene) { previewForgeCard(scene.after, scene.outcome) }
        val receipt = remember(scene, run) {
            ForgeReceipt(
                id = "preview-$run", copyId = card.id, before = scene.before, after = scene.after,
                outcome = scene.outcome, costHalfPoints = 0, at = Instant.EPOCH,
                acknowledged = false, materialId = null,
            )
        }
        var finished by rememberSaveable { mutableStateOf(false) }
        ForgePresentation(
            card,
            receipt,
            receipt.id.takeUnless { finished },
            { finished = true },
            onBack,
            modifier,
        ) { playing, art ->
            PreviewStage(
                scene.outcome.label,
                forgePreviewDetail(scene.before, scene.after, scene.outcome),
                !playing,
                replay,
                onBack,
                art = art,
            )
        }
    }
}
