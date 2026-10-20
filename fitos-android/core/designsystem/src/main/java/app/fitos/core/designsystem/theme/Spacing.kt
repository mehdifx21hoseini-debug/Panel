package app.fitos.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** 4dp spatial rhythm plus the semantic gaps used between related items, sections and major blocks. */
object FitSpacing {
    val s2 = 2.dp
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp

    val gutter = s16
    val gapRelated = s8
    val gapSection = s16
    val gapMajor = s32

    /** Minimum touch target. */
    val target = 48.dp
}
