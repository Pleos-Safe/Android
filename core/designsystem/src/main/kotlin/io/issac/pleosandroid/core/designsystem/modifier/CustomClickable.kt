package io.issac.pleosandroid.core.designsystem.modifier

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import io.issac.pleosandroid.core.designsystem.theme.PleosMotion
import io.issac.pleosandroid.core.designsystem.theme.PleosShape

/**
 * Starts press feedback at the touch position, including inside scrolling layouts.
 * Put the surface background and border after this modifier so they scale with the content.
 */
@Composable
fun Modifier.customClickable(
    shape: Shape = PleosShape.button,
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    rippleColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    return clickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
        .pressOnDown(source, enabled, onClick = onClick)
        .shapedRipple(source, shape, rippleColor, enabled)
}

@Composable
internal fun Modifier.pressOnDown(
    source: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    return then(InstantTapElement(source, onClick)).pressScale(source)
}

private data class InstantTapElement(val source: MutableInteractionSource, val onClick: () -> Unit) :
    ModifierNodeElement<InstantTapNode>() {
    override fun create() = InstantTapNode(source, onClick)
    override fun update(node: InstantTapNode) = node.update(source, onClick)
    override fun InspectorInfo.inspectableProperties() {
        name = "pressOnDown"
    }
}

// The inner detector handles touch. The outer clickable, selectable or toggleable handles
// keyboard activation, focus and accessibility.
private class InstantTapNode(private var source: MutableInteractionSource, private var onClick: () -> Unit) :
    DelegatingNode() {
    private val detector = delegate(
        SuspendingPointerInputModifierNode(
            PointerInputEventHandler {
                detectTapGestures(
                    onPress = { offset ->
                        val gestureSource = source
                        val press = PressInteraction.Press(offset)
                        gestureSource.tryEmit(press)
                        var released = false
                        try {
                            released = tryAwaitRelease()
                        } finally {
                            val end = if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                            gestureSource.tryEmit(end)
                        }
                    },
                    onTap = { onClick() },
                )
            },
        ),
    )

    fun update(source: MutableInteractionSource, onClick: () -> Unit) {
        if (this.source !== source) detector.resetPointerInputHandler()
        this.source = source
        this.onClick = onClick
    }
}

// Draw the scale so the touch target, focus bounds and layout stay at their original size.
@Composable
private fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val animatedScale = animateFloatAsState(
        targetValue = if (pressed) PleosMotion.PRESSED_SCALE else 1f,
        animationSpec = tween(PleosMotion.PRESS_DURATION_MILLIS),
        label = "Pleos press",
    )
    return drawWithContent {
        scale(animatedScale.value) { this@drawWithContent.drawContent() }
    }
}

internal fun Modifier.shapedRipple(
    source: InteractionSource,
    shape: Shape,
    color: Color,
    enabled: Boolean = true,
): Modifier = clip(shape).indication(source, if (enabled) ripple(color = color) else null)
