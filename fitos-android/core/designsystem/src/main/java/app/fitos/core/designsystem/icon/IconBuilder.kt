package app.fitos.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke width of the outlined icon set, in viewport units (24 = icon size). */
const val ICON_STROKE = 1.8f

/**
 * Builds one 24dp icon from SVG path data. Paths are drawn in black and tinted by `Icon`,
 * so every icon follows the colour of its container. Directional icons pass [autoMirror].
 */
internal fun fitIcon(
    name: String,
    strokes: List<String>,
    fills: List<String> = emptyList(),
    autoMirror: Boolean = false,
): ImageVector = ImageVector.Builder(
    name = "FitIcons.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror,
).apply {
    fills.forEach { d -> addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)) }
    strokes.forEach { d ->
        addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = ICON_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()
