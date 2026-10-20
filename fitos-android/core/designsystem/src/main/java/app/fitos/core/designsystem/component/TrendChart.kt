package app.fitos.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitos.core.designsystem.theme.FitFonts
import app.fitos.core.designsystem.theme.FitTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** One day on the chart: the logged value and, when known, the smoothed trend. */
data class ChartPoint(val x: Long, val value: Double, val trend: Double?)

/**
 * Trend chart from the design system: trend line with a 10% area, light dots for the logged
 * values, horizontal grid, an end dot and an inverse tooltip pill on touch. Time runs in Persian
 * reading direction: oldest on the right, newest on the left. [description] is read by TalkBack.
 */
@Composable
fun TrendChart(
    points: List<ChartPoint>,
    description: String,
    valueLabel: (Double) -> String,
    dateLabel: (Long) -> String,
    tooltip: (ChartPoint) -> String,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    goalLabel: String? = null,
    height: Dp = 200.dp,
) {
    val colors = FitTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = FitFonts.Display, fontSize = 12.sp, color = colors.subtle)
    val tipStyle = TextStyle(fontFamily = FitFonts.Body, fontSize = 13.sp, color = colors.inverseOnSurface)
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    if (points.isEmpty()) return

    val values = points.flatMap { listOfNotNull(it.value, it.trend) } + listOfNotNull(goal)
    val (low, high, step) = niceRange(values.min(), values.max())
    val minX = points.first().x
    val maxX = points.last().x

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { selected = nearest(points, it.x, 40.dp.toPx(), size.width - 8.dp.toPx()) }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures { change, _ ->
                    selected = nearest(points, change.position.x, 40.dp.toPx(), size.width - 8.dp.toPx())
                }
            },
    ) {
        val left = 40.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun xOf(x: Long): Float = if (maxX == minX) (left + right) / 2 else right - (x - minX).toFloat() / (maxX - minX) * (right - left)
        fun yOf(v: Double): Float = (bottom - (v - low) / (high - low) * (bottom - top)).toFloat()

        // Grid and value labels (on the left, where the newest data is read).
        var v = low
        while (v <= high + 1e-9) {
            val y = yOf(v)
            drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(valueLabel(v), labelStyle)
            drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y - text.size.height / 2f))
            v += step
        }
        // Dates: oldest under the right edge, newest under the left edge.
        val oldest = measurer.measure(dateLabel(minX), labelStyle)
        drawText(oldest, topLeft = Offset(right - oldest.size.width, bottom + 4.dp.toPx()))
        if (maxX != minX) {
            val newest = measurer.measure(dateLabel(maxX), labelStyle)
            drawText(newest, topLeft = Offset(left, bottom + 4.dp.toPx()))
        }

        if (goal != null) {
            val y = yOf(goal)
            drawLine(
                colors.onSurfaceVariant, Offset(left, y), Offset(right, y),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            goalLabel?.let {
                val text = measurer.measure(it, labelStyle.copy(color = colors.onSurfaceVariant))
                drawText(text, topLeft = Offset(right - text.size.width, y - text.size.height - 2.dp.toPx()))
            }
        }

        // Logged values: small, quiet dots.
        points.forEach { p ->
            drawCircle(colors.primary.copy(alpha = 0.35f), radius = 3.dp.toPx(), center = Offset(xOf(p.x), yOf(p.value)))
        }

        // Trend line with its area.
        val trend = points.filter { it.trend != null }
        if (trend.size >= 2) {
            val line = Path()
            trend.forEachIndexed { i, p -> if (i == 0) line.moveTo(xOf(p.x), yOf(p.trend!!)) else line.lineTo(xOf(p.x), yOf(p.trend!!)) }
            val area = Path().apply {
                addPath(line)
                lineTo(xOf(trend.last().x), bottom)
                lineTo(xOf(trend.first().x), bottom)
                close()
            }
            drawPath(area, colors.primary.copy(alpha = 0.10f))
            drawPath(line, colors.primary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val end = trend.last()
            drawCircle(colors.surface, radius = 5.dp.toPx(), center = Offset(xOf(end.x), yOf(end.trend!!)))
            drawCircle(colors.primary, radius = 5.dp.toPx(), center = Offset(xOf(end.x), yOf(end.trend!!)), style = Stroke(2.5.dp.toPx()))
        }

        selected?.let { index ->
            val p = points[index]
            val x = xOf(p.x)
            drawLine(colors.outline, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(colors.primary, radius = 4.5.dp.toPx(), center = Offset(x, yOf(p.value)))
            val text = measurer.measure(tooltip(p), tipStyle)
            val pad = 8.dp.toPx()
            val w = text.size.width + pad * 2
            val h = text.size.height + pad
            val boxLeft = (x - w / 2).coerceIn(0f, size.width - w)
            drawRoundRect(colors.inverseSurface, Offset(boxLeft, 0f), Size(w, h), CornerRadius(h / 2))
            drawText(text, topLeft = Offset(boxLeft + pad, pad / 2))
        }
    }
}

/** Index of the point closest in time to screen position [x]; right edge = oldest, left = newest. */
private fun nearest(points: List<ChartPoint>, x: Float, left: Float, right: Float): Int {
    val minX = points.first().x
    val maxX = points.last().x
    val fraction = ((right - x) / (right - left)).coerceIn(0f, 1f)
    val target = minX + fraction * (maxX - minX)
    return points.indices.minBy { abs(points[it].x - target) }
}

/** Rounded axis bounds and a step that gives three to five gridlines. */
internal fun niceRange(min: Double, max: Double): Triple<Double, Double, Double> {
    val span = (max - min).coerceAtLeast(1.0)
    val step = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0).firstOrNull { span / it <= 4 } ?: 100.0
    val low = floor(min / step) * step
    var high = ceil(max / step) * step
    if (high - low < step * 2) high = low + step * 2
    return Triple(low, high, step)
}
