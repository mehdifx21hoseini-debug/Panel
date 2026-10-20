package app.fitos.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Neutral body silhouettes for the start → now → goal journey (sprite i-body-*, 44×96). */
enum class BodyShape(internal val torso: FloatArray, internal val arms: FloatArray, internal val legs: FloatArray) {
    // x, y, w, h, r — left limb; the right one is mirrored around x = 22.
    START(floatArrayOf(8f, 18f, 28f, 36f, 11f), floatArrayOf(1.5f, 21f, 5.5f, 29f, 2.75f), floatArrayOf(11f, 50f, 10f, 42f, 5f)),
    NOW(floatArrayOf(10f, 18f, 24f, 36f, 9f), floatArrayOf(3.5f, 21f, 5f, 29f, 2.5f), floatArrayOf(12.5f, 50f, 9f, 42f, 4.5f)),
    GOAL(floatArrayOf(12f, 18f, 20f, 36f, 7f), floatArrayOf(5.5f, 21f, 5f, 29f, 2.5f), floatArrayOf(13.5f, 50f, 8.5f, 42f, 4.25f)),
}

/** Draws [shape] filled with [color], or as a dashed outline when [outlined] (the goal). */
@Composable
fun BodyFigure(shape: BodyShape, color: Color, modifier: Modifier = Modifier, outlined: Boolean = false) {
    Canvas(modifier.size(width = 44.dp, height = 96.dp)) {
        val k = size.width / 44f
        val style: DrawStyle = if (outlined) {
            Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
        } else {
            Fill
        }
        fun rect(r: FloatArray, mirror: Boolean) {
            val x = if (mirror) 44f - r[0] - r[2] else r[0]
            drawRoundRect(color, Offset(x * k, r[1] * k), Size(r[2] * k, r[3] * k), CornerRadius(r[4] * k), style = style)
        }
        drawCircle(color, radius = 7f * k, center = Offset(22f * k, 9f * k), style = style)
        rect(shape.torso, mirror = false)
        rect(shape.arms, mirror = false)
        rect(shape.arms, mirror = true)
        rect(shape.legs, mirror = false)
        rect(shape.legs, mirror = true)
    }
}
