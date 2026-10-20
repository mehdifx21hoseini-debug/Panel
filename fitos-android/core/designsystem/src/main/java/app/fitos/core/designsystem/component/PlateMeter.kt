package app.fitos.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.theme.FitTheme

/** One plate: how full it is (0..1) and its module colour. Unlogged = 0, never shown as done. */
data class Plate(val fraction: Float, val color: Color)

/** Plate heights from the collar outwards, in dp: biggest plates sit next to the collar. */
private val PlateHeights = listOf(108f, 108f, 100f, 100f, 92f, 84f)

/**
 * Signature progress visual: a barbell whose plates fill up as the day's goals are met.
 * [plates] are ordered from the collar outwards (max 6) and are mirrored on both sides.
 * Drawn left-to-right in every locale, like a real bar. [description] is read by TalkBack.
 */
@Composable
fun PlateMeter(plates: List<Plate>, description: String, modifier: Modifier = Modifier) {
    val colors = FitTheme.colors
    val animated = plates.take(PlateHeights.size).map { plate ->
        val fill by animateFloatAsState(plate.fraction.coerceIn(0f, 1f), tween(500), label = "plate")
        plate.copy(fraction = fill)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
    ) {
        drawBarbell(animated, colors.track, colors.steel, colors.steelDark)
    }
}

private fun DrawScope.drawBarbell(plates: List<Plate>, track: Color, steel: Color, steelDark: Color) {
    val n = plates.size
    val capW = 10.dp.toPx(); val capH = 14.dp.toPx()
    val collarW = 7.dp.toPx(); val collarH = 22.dp.toPx()
    val shaftH = 7.dp.toPx()
    val gap = 3.dp.toPx()
    val sideW = n * 15.dp.toPx() + (n - 1).coerceAtLeast(0) * gap
    val fixed = 2 * (capW + gap + sideW + gap + collarW)
    // Shaft takes what is left (16..84dp); if even that does not fit, everything scales down.
    val shaftW = (size.width - fixed).coerceIn(16.dp.toPx(), 84.dp.toPx())
    val total = fixed + shaftW
    val scale = if (total > size.width) size.width / total else 1f
    val cy = size.height / 2f
    var x = (size.width - total * scale) / 2f

    fun bar(w: Float, h: Float, r: Float, color: Color) {
        drawRoundRect(color, Offset(x, cy - h * scale / 2f), Size(w * scale, h * scale), CornerRadius(r * scale))
        x += w * scale
    }
    fun plate(p: Plate, heightDp: Float) {
        val w = 15.dp.toPx() * scale
        val h = heightDp.dp.toPx() * scale
        val top = cy - h / 2f
        val r = CornerRadius(5.dp.toPx() * scale)
        drawRoundRect(track, Offset(x, top), Size(w, h), r)
        if (p.fraction > 0f) {
            clipRect(x, top + h * (1f - p.fraction), x + w, top + h) {
                drawRoundRect(p.color, Offset(x, top), Size(w, h), r)
            }
        }
        x += w
    }

    bar(capW, capH, 3.dp.toPx(), steel)
    x += gap * scale
    for (i in n - 1 downTo 0) { plate(plates[i], PlateHeights[i]); if (i > 0) x += gap * scale }
    x += gap * scale
    bar(collarW, collarH, 2.dp.toPx(), steelDark)
    bar(shaftW, shaftH, 4.dp.toPx(), steel)
    bar(collarW, collarH, 2.dp.toPx(), steelDark)
    x += gap * scale
    for (i in 0 until n) { plate(plates[i], PlateHeights[i]); if (i < n - 1) x += gap * scale }
    x += gap * scale
    bar(capW, capH, 3.dp.toPx(), steel)
}
