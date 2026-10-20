package app.fitos.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.fitos.core.designsystem.R

/** Bundled faces: Vazirmatn reads all text; Changa is only for display numerals. Weights stay at or below 600. */
object FitFonts {
    val Body = FontFamily(
        Font(R.font.vazirmatn_regular, FontWeight.Normal),
        Font(R.font.vazirmatn_medium, FontWeight.Medium),
        Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    )
    val Display = FontFamily(
        Font(R.font.changa_medium, FontWeight.Medium),
        Font(R.font.changa_semibold, FontWeight.SemiBold),
    )
}

private fun style(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Float): TextStyle = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    // Persian is cursive: never track it.
    letterSpacing = TextUnit.Unspecified,
)

/** The 15 Material 3 styles, values from the design-system type scale (minimum size 12). */
val FitTypography = Typography(
    displayLarge = style(FitFonts.Display, FontWeight.SemiBold, 56, 1.05f),
    displayMedium = style(FitFonts.Display, FontWeight.SemiBold, 44, 1.08f),
    displaySmall = style(FitFonts.Display, FontWeight.SemiBold, 34, 1.12f),
    headlineLarge = style(FitFonts.Body, FontWeight.SemiBold, 30, 1.25f),
    headlineMedium = style(FitFonts.Body, FontWeight.SemiBold, 26, 1.25f),
    headlineSmall = style(FitFonts.Body, FontWeight.SemiBold, 22, 1.3f),
    titleLarge = style(FitFonts.Body, FontWeight.SemiBold, 20, 1.35f),
    titleMedium = style(FitFonts.Body, FontWeight.Medium, 16, 1.5f),
    titleSmall = style(FitFonts.Body, FontWeight.Medium, 14, 1.45f),
    bodyLarge = style(FitFonts.Body, FontWeight.Normal, 16, 1.6f),
    bodyMedium = style(FitFonts.Body, FontWeight.Normal, 14, 1.55f),
    bodySmall = style(FitFonts.Body, FontWeight.Normal, 12, 1.45f),
    labelLarge = style(FitFonts.Body, FontWeight.Medium, 14, 1.4f),
    labelMedium = style(FitFonts.Body, FontWeight.Medium, 12, 1.35f),
    labelSmall = style(FitFonts.Body, FontWeight.Medium, 12, 1.3f),
)

/** Changa numerals at a given size, for values inside text rows (stat values, day numbers). */
fun numeralStyle(size: Int, weight: FontWeight = FontWeight.SemiBold): TextStyle =
    style(FitFonts.Display, weight, size, 1.15f)
