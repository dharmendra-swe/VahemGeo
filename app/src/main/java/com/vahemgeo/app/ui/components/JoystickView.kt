package com.vahemgeo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vahemgeo.app.ui.theme.CyberTeal
import com.vahemgeo.app.ui.theme.ElectricLime
import com.vahemgeo.app.ui.theme.PolarWhite
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun JoystickView(
    modifier: Modifier = Modifier,
    size: Dp = 135.dp,
    knobSize: Dp = 48.dp,
    onJoystickMoved: (bearingDegrees: Float, intensity: Float) -> Unit,
    onJoystickReleased: () -> Unit
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val knobSizePx = with(density) { knobSize.toPx() }
    val maxRadiusPx = (sizePx - knobSizePx) / 2f

    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .size(size)
            .background(
                color = CyberTeal.copy(alpha = 0.85f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val centerX = sizePx / 2f
                        val centerY = sizePx / 2f
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        val dist = hypot(dx, dy)
                        val clampedDist = dist.coerceAtMost(maxRadiusPx)
                        val angle = atan2(dy, dx)

                        knobOffset = Offset(
                            x = clampedDist * cos(angle),
                            y = clampedDist * sin(angle)
                        )

                        val intensity = (clampedDist / maxRadiusPx).coerceIn(0f, 1f)
                        var bearing = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
                        if (bearing < 0) bearing += 360f

                        onJoystickMoved(bearing, intensity)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = knobOffset + dragAmount
                        val dist = hypot(newOffset.x, newOffset.y)
                        val clampedDist = dist.coerceAtMost(maxRadiusPx)
                        val angle = atan2(newOffset.y, newOffset.x)

                        knobOffset = Offset(
                            x = clampedDist * cos(angle),
                            y = clampedDist * sin(angle)
                        )

                        val intensity = (clampedDist / maxRadiusPx).coerceIn(0f, 1f)
                        var bearing = Math.toDegrees(atan2(newOffset.x.toDouble(), -newOffset.y.toDouble())).toFloat()
                        if (bearing < 0) bearing += 360f

                        onJoystickMoved(bearing, intensity)
                    },
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onJoystickReleased()
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onJoystickReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Luminous Electric Lime Ring
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(sizePx / 2f, sizePx / 2f)
            drawCircle(
                color = ElectricLime.copy(alpha = 0.6f),
                radius = maxRadiusPx + knobSizePx / 2f,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        // Inner Luminous Electric Lime Knob with White Center Cap
        Box(
            modifier = Modifier
                .size(knobSize)
                .background(
                    color = ElectricLime,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = PolarWhite,
                        shape = CircleShape
                    )
            )
        }
    }
}
