package app.fitos.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.training.Muscle

enum class BodySide { FRONT, BACK }

/**
 * Muscle-map pictogram from the design system (viewBox 120×240, 14 regions). Primary muscles
 * take the training colour with a hatch, secondary ones a softer mix, so the meaning never
 * rests on colour alone. [description] (e.g. the muscle names) is read by TalkBack.
 */
@Composable
fun MuscleMap(
    side: BodySide,
    primary: Set<Muscle>,
    secondary: Set<Muscle>,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    description: String? = null,
) {
    val colors = FitTheme.colors
    val shapes = remember { MuscleShapes }
    val hi = colors.training.fill
    val soft = lerp(colors.muscle, hi, 0.45f)
    Canvas(
        modifier
            .width(width)
            .height(width * 2)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        scale(size.width / 120f, size.height / 240f, pivot = Offset.Zero) {
            shapes.base.forEach { drawPath(it, colors.muscleBody) }
            val regions = if (side == BodySide.FRONT) shapes.front else shapes.back
            regions.forEach { (muscle, path) ->
                val fill = when (muscle) {
                    in primary -> hi
                    in secondary -> soft
                    else -> colors.muscle
                }
                drawPath(path, fill)
                if (muscle in primary) hatch(path)
            }
        }
    }
}

/** The side that shows more of the highlighted muscles (front when equal). */
fun bestSide(primary: Set<Muscle>, secondary: Set<Muscle>): BodySide {
    fun score(front: Boolean) = primary.count { if (front) it.front else it.back } * 2 + secondary.count { if (front) it.front else it.back }
    return if (score(front = false) > score(front = true)) BodySide.BACK else BodySide.FRONT
}

private fun DrawScope.hatch(path: Path) {
    clipPath(path) {
        var x = -240f
        while (x < 120f) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(x, 240f), Offset(x + 240f, 0f), strokeWidth = 1.3f)
            x += 4f
        }
    }
}

private object MuscleShapes {
    private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()
    private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }
    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply { addRoundRect(RoundRect(Rect(Offset(x, y), Size(w, h)), CornerRadius(r))) }
    private fun group(vararg parts: Path) = Path().apply { parts.forEach { addPath(it) } }

    val base: List<Path> = listOf(
        circle(60f, 18f, 12f), rect(54f, 28f, 12f, 10f, 4f),
        svg("M36 40Q60 34 84 40L88 62Q86 96 78 118H42Q34 96 32 62Z"),
        rect(18f, 44f, 15f, 40f, 7.5f), rect(87f, 44f, 15f, 40f, 7.5f),
        rect(14f, 85f, 12f, 38f, 6f), rect(94f, 85f, 12f, 38f, 6f),
        circle(19f, 128f, 6f), circle(101f, 128f, 6f),
        svg("M42 116H78L82 138H38Z"),
        rect(38f, 134f, 20f, 54f, 9f), rect(62f, 134f, 20f, 54f, 9f),
        rect(40f, 188f, 16f, 42f, 7f), rect(64f, 188f, 16f, 42f, 7f),
        ellipse(47f, 233f, 9f, 4f), ellipse(73f, 233f, 9f, 4f),
    )

    private val delts = group(ellipse(33f, 49f, 9f, 8f), ellipse(87f, 49f, 9f, 8f))
    private val arms = group(ellipse(25.5f, 67f, 5.5f, 13f), ellipse(94.5f, 67f, 5.5f, 13f))
    private val forearms = group(ellipse(20f, 103f, 4.5f, 13f), ellipse(100f, 103f, 4.5f, 13f))

    val front: List<Pair<Muscle, Path>> = listOf(
        Muscle.DELTS to delts,
        Muscle.CHEST to group(svg("M59 46Q46 44 39 50Q38 62 44 68Q52 71 59 68Z"), svg("M61 46Q74 44 81 50Q82 62 76 68Q68 71 61 68Z")),
        Muscle.BICEPS to arms,
        Muscle.FOREARMS to forearms,
        Muscle.ABS to group(
            rect(51f, 74f, 8f, 10f, 3f), rect(61f, 74f, 8f, 10f, 3f), rect(51f, 86f, 8f, 10f, 3f),
            rect(61f, 86f, 8f, 10f, 3f), rect(51f, 98f, 8f, 12f, 3f), rect(61f, 98f, 8f, 12f, 3f),
        ),
        Muscle.OBLIQUES to group(svg("M43 74L49 76V108L44 110Q40 92 43 74Z"), svg("M77 74L71 76V108L76 110Q80 92 77 74Z")),
        Muscle.QUADS to group(ellipse(48f, 161f, 8f, 23f), ellipse(72f, 161f, 8f, 23f)),
        Muscle.CALVES to group(ellipse(48f, 207f, 5.5f, 14f), ellipse(72f, 207f, 5.5f, 14f)),
    )

    val back: List<Pair<Muscle, Path>> = listOf(
        Muscle.TRAPS to svg("M60 36L76 45L60 68L44 45Z"),
        Muscle.DELTS to delts,
        Muscle.LATS to group(svg("M44 58Q37 72 42 98L56 89V67Z"), svg("M76 58Q83 72 78 98L64 89V67Z")),
        Muscle.TRICEPS to arms,
        Muscle.FOREARMS to forearms,
        Muscle.LOWER_BACK to rect(52f, 92f, 16f, 22f, 5f),
        Muscle.GLUTES to group(ellipse(50f, 128f, 10f, 9f), ellipse(70f, 128f, 10f, 9f)),
        Muscle.HAMSTRINGS to group(ellipse(48f, 164f, 8f, 21f), ellipse(72f, 164f, 8f, 21f)),
        Muscle.CALVES to group(ellipse(48f, 205f, 6.5f, 15f), ellipse(72f, 205f, 6.5f, 15f)),
    )
}
