package com.ravenemu.v3.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GbDpad(onButton)
            Box(Modifier.width(144.dp).height(144.dp)) {
                GbMomentaryButton(
                    button = GbButton.B,
                    label = "B",
                    onButton = onButton,
                    diameter = 66,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                GbMomentaryButton(
                    button = GbButton.A,
                    label = "A",
                    onButton = onButton,
                    diameter = 66,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GbMomentaryButton(
                GbButton.SELECT, "SELECT", onButton, diameter = 36,
                modifier = Modifier.width(78.dp),
            )
            Spacer(Modifier.width(18.dp))
            GbMomentaryButton(
                GbButton.START, "START", onButton, diameter = 36,
                modifier = Modifier.width(78.dp),
            )
        }
    }
}

@Composable
private fun GbDpad(onButton: (GbButton, Boolean) -> Unit) {
    var held by remember { mutableStateOf(emptySet<GbButton>()) }
    val handler by rememberUpdatedState(onButton)
    Canvas(
        Modifier.size(148.dp)
            .semantics { contentDescription = "Croix directionnelle"; role = Role.Button }
            // A stable key avoids cancelling a held gesture at every video frame.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var pressed = emptySet<GbButton>()
                    fun update(next: Set<GbButton>) {
                        (pressed - next).forEach { handler(it, false) }
                        (next - pressed).forEach { handler(it, true) }
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
        drawRoundRect(ControlFill, topLeft = vertical, size = verticalSize, cornerRadius = corner)
        drawRoundRect(ControlFill, topLeft = horizontal, size = horizontalSize, cornerRadius = corner)
        val stroke = Stroke(width = 2.dp.toPx())
        val outline = if (held.isEmpty()) ControlBorder else ControlAccent
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
) {
    var down by remember { mutableStateOf(false) }
    val handler by rememberUpdatedState(onButton)
    val pill = button == GbButton.SELECT || button == GbButton.START
    val shape = if (pill) RoundedCornerShape(50) else CircleShape
    Box(
        modifier
            .height(diameter.dp)
            .then(if (pill) Modifier else Modifier.width(diameter.dp))
            .semantics { contentDescription = label; role = Role.Button }
            .border(2.dp, if (down) ControlAccent else ControlBorder, shape)
            .background(if (down) ControlAccent.copy(alpha = .25f) else ControlFill, shape)
            .pointerInput(button) {
                detectTapGestures(onPress = {
                    down = true
                    handler(button, true)
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
            color = Color.White.copy(alpha = if (down) 1f else .86f),
        )
    }
}
