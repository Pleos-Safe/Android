package io.issac.pleosandroid.core.designsystem.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object PleosCorners {
    val buttonRadius = 8.dp
    val chipRadius = 20.dp
    val stageCut = 20.dp
    val insetCut = 12.dp
}

object PleosShape {
    val button = RoundedCornerShape(PleosCorners.buttonRadius)
    val chip = RoundedCornerShape(PleosCorners.chipRadius)
    val stage = CutCornerShape(PleosCorners.stageCut)
    val inset = CutCornerShape(PleosCorners.insetCut)
}

internal val PleosShapes = Shapes(small = PleosShape.button)
