package com.ravenemu.v3.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val ControlBorder = Color.White.copy(alpha = 0.64f)
private val ControlFill = Color.White.copy(alpha = 0.13f)
private val ControlAccent = Color(0xFFA78BFA)

/**
 * Portrait controls inspired by RavenEmu 0.8.1 TouchControlsView:
 * one movable D-pad with diagonals, A/B and Start/Select.
 */
@Composable
fun GbTouchControls(
    onButton: (GbButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    haptics: Boolean = false,
    scalePercent: Int = 100,
    verticalGapDp: Int = 46,
    highContrast: Boolean = false,
) {
    // Default portrait geometry follows the user-provided control layout:
    // D-pad at the left, A at the upper-right, B lower and inward, and
    // separated SELECT/START below. Fit the action cluster on narrow screens.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cluster = (GbTouchLayout.clusterDp(maxWidth.value) *
            scalePercent.coerceIn(80, 115) / 100f)
            .coerceAtMost((maxWidth.value - 104f).coerceAtLeast(60f)).dp
        val buttonDiameter = (60f * scalePercent.coerceIn(80, 115) / 100f)
            .roundToInt().coerceIn(48, 69)
        val actionWidth = minOf(144.dp, (maxWidth - 44.dp - cluster).coerceAtLeast(60.dp))
        val selectStartGap = minOf(50.dp, (maxWidth - 168.dp).coerceAtLeast(8.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalGapDp.coerceIn(20, 80).dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GbDpad(onButton = onButton, dimension = cluster,
                    haptics = haptics, highContrast = highContrast)
                Box(Modifier.width(actionWidth).height(cluster)) {
                    // A high and close to the right edge; B below and inward,
                    // matching the retouched reference screenshot.
                    GbMomentaryButton(
                        button = GbButton.B,
                        label = "B",
                        onButton = onButton,
                        diameter = buttonDiameter,
                        modifier = Modifier.align(Alignment.BottomStart),
                        haptics = haptics, highContrast = highContrast,
                    )
                    GbMomentaryButton(
                        button = GbButton.A,
                        label = "A",
                        onButton = onButton,
                        diameter = buttonDiameter,
                        modifier = Modifier.align(Alignment.TopEnd),
                        haptics = haptics, highContrast = highContrast,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GbMomentaryButton(
                    GbButton.SELECT, "SELECT", onButton, diameter = 40,
                    modifier = Modifier.width(84.dp),
                    haptics = haptics, highContrast = highContrast,
                    haptics = haptics, highContrast = highContrast,
                )
                Spacer(Modifier.width(selectStartGap))
                GbMomentaryButton(
                    GbButton.START, "START", onButton, diameter = 40,
                    modifier = Modifier.width(84.dp),
                )
            }
        }
    }
}

@Composable
private fun GbDpad(
    onButton: (GbButton, Boolean) -> Unit, dimension: Dp,
    haptics: Boolean, highContrast: Boolean,
) {
    var held by remember { mutableStateOf(emptySet<GbButton>()) }
    val handler by rememberUpdatedState(onButton)
    val hapticsEnabled by rememberUpdatedState(haptics)
    val feedback = LocalHapticFeedback.current
    Canvas(
        Modifier.size(dimension)
            .semantics { contentDescription = "Croix directionnelle"; role = Role.Button }
            // A stable key avoids cancelling a held gesture at every video frame.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var pressed = emptySet<GbButton>()
                    fun update(next: Set<GbButton>) {
                        (pressed - next).forEach { handler(it, false) }
                        (next - pressed).forEach { handler(it, true) }
                        if (pressed.isEmpty() && next.isNotEmpty() && hapticsEnabled) {
                            feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        pressed = next
                        held = next
                    }
                    try {
                        update(
                            GbDpadHitTest.buttonsAt(
                                down.position.x, down.position.y,
                                size.width.toFloat(), size.height.toFloat(),
                            ),
                        )
                        do {
                            val event = awaitPointerEvent()
                            val next = event.changes.filter { it.pressed }
                                .flatMap { change ->
                                    GbDpadHitTest.buttonsAt(
                                        change.position.x, change.position.y,
                                        size.width.toFloat(), size.height.toFloat(),
                                    )
                                }.toSet()
                            update(next)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        update(emptySet())
                    }
                }
            },
    ) {
        val arm = size.width / 3f
        val corner = CornerRadius(8.dp.toPx())
        val vertical = Offset((size.width - arm) / 2f, 0f)
        val horizontal = Offset(0f, (size.height - arm) / 2f)
        val verticalSize = Size(arm, size.height)
        val horizontalSize = Size(size.width, arm)
        drawRoundRect(if (highContrast) Color.White.copy(alpha = 0.27f) else ControlFill,
            topLeft = vertical, size = verticalSize, cornerRadius = corner)
        drawRoundRect(if (highContrast) Color.White.copy(alpha = 0.27f) else ControlFill,
            topLeft = horizontal, size = horizontalSize, cornerRadius = corner)
        val stroke = Stroke(width = 2.dp.toPx())
        val outline = if (held.isNotEmpty()) ControlAccent
            else if (highContrast) Color.White else ControlBorder
        drawRoundRect(outline, topLeft = vertical, size = verticalSize,
            cornerRadius = corner, style = stroke)
        drawRoundRect(outline, topLeft = horizontal, size = horizontalSize,
            cornerRadius = corner, style = stroke)
    }
}

@Composable
private fun GbMomentaryButton(
    button: GbButton,
    label: String,
    onButton: (GbButton, Boolean) -> Unit,
    diameter: Int,
    modifier: Modifier = Modifier,
    haptics: Boolean = false,
    highContrast: Boolean = false,
) {
    var down by remember { mutableStateOf(false) }
    val handler by rememberUpdatedState(onButton)
    val hapticsEnabled by rememberUpdatedState(haptics)
    val feedback = LocalHapticFeedback.current
    val pill = button == GbButton.SELECT || button == GbButton.START
    val shape = if (pill) RoundedCornerShape(50) else CircleShape
    Box(
        modifier
            .height(diameter.dp)
            .then(if (pill) Modifier else Modifier.width(diameter.dp))
            .semantics { contentDescription = label; role = Role.Button }
            .border(2.dp, if (down) ControlAccent
                else if (highContrast) Color.White else ControlBorder, shape)
            .background(if (down) ControlAccent.copy(alpha = .25f)
                else if (highContrast) Color.White.copy(alpha = .27f) else ControlFill, shape)
            .pointerInput(button) {
                detectTapGestures(onPress = {
                    down = true
                    handler(button, true)
                    if (hapticsEnabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        tryAwaitRelease()
                    } finally {
                        handler(button, false)
                        down = false
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (pill) 11.sp else 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = if (down || highContrast) 1f else .86f),
        )
    }
}

/**
 * Ergonomic thumb cluster sizing shared by Android and iOS.
 * D-pad has 16 dp left padding and its dimension never exceeds 132 dp.
 * The action cluster is inset 28 dp from the right edge on wide phones.
 */
object GbTouchLayout {
    fun clusterDp(viewportWidthDp: Float): Float =
        ((viewportWidthDp - 32f) / 2f).coerceIn(0f, 132f)
}
