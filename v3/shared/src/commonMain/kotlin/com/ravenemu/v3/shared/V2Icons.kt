package com.ravenemu.v3.shared

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * Exact 24x24 vector path data from RavenEmu 0.8.1's res/drawable XML assets.
 * Kept in commonMain so that Android and iOS render the same V2 icons.
 * These are not newly approximated pictograms.
 */
enum class V2IconType { SETTINGS, GENERAL, AUDIO, VIDEO, INTERFACE, ABOUT }

private fun v2IconPath(type: V2IconType): String = when (type) {
    V2IconType.SETTINGS ->
        "M19.43,12.98C19.47,12.66 19.5,12.33 19.5,12C19.5,11.67 19.47,11.34 19.43,11.02L21.54,9.37C21.73,9.22 21.78,8.95 21.66,8.73L19.66,5.27C19.54,5.05 19.29,4.96 19.06,5.05L16.57,6.05C16.05,5.65 15.49,5.32 14.88,5.07L14.5,2.42C14.47,2.18 14.25,2 14,2H10C9.75,2 9.53,2.18 9.5,2.42L9.12,5.07C8.51,5.32 7.95,5.65 7.43,6.05L4.94,5.05C4.71,4.96 4.46,5.05 4.34,5.27L2.34,8.73C2.22,8.95 2.27,9.22 2.46,9.37L4.57,11.02C4.53,11.34 4.5,11.67 4.5,12C4.5,12.33 4.53,12.66 4.57,12.98L2.46,14.63C2.27,14.78 2.22,15.05 2.34,15.27L4.34,18.73C4.46,18.95 4.71,19.04 4.94,18.95L7.43,17.95C7.95,18.35 8.51,18.68 9.12,18.93L9.5,21.58C9.53,21.82 9.75,22 10,22H14C14.25,22 14.47,21.82 14.5,21.58L14.88,18.93C15.49,18.68 16.05,18.35 16.57,17.95L19.06,18.95C19.29,19.04 19.54,18.95 19.66,18.73L21.66,15.27C21.78,15.05 21.73,14.78 21.54,14.63L19.43,12.98ZM12,15.5C10.07,15.5 8.5,13.93 8.5,12C8.5,10.07 10.07,8.5 12,8.5C13.93,8.5 15.5,10.07 15.5,12C15.5,13.93 13.93,15.5 12,15.5Z"
    V2IconType.GENERAL ->
        "M3,5H13V7H3ZM17,5H21V7H17ZM13,3H17V9H13ZM3,11H7V13H3ZM11,11H21V13H11ZM7,9H11V15H7ZM3,17H13V19H3ZM17,17H21V19H17ZM13,15H17V21H13Z"
    V2IconType.AUDIO ->
        "M3,9H7L12,4V20L7,15H3ZM15,8V16C17,15 17,9 15,8ZM15,3V5C21,7 21,17 15,19V21C24,18 24,6 15,3Z"
    V2IconType.VIDEO ->
        "M3,3H21V17H13V19H17V21H7V19H11V17H3ZM5,5V15H19V5Z"
    V2IconType.INTERFACE ->
        "M3,3H11V11H3ZM13,3H21V11H13ZM3,13H11V21H3ZM13,13H21V21H13Z"
    V2IconType.ABOUT ->
        "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z"
}

@Composable
fun V2Icon(
    type: V2IconType,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val path = remember(type) { PathParser().parsePathString(v2IconPath(type)).toPath() }
    Canvas(modifier.size(24.dp)) {
        scale(scaleX = size.width / 24f, scaleY = size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, color = tint)
        }
    }
}
