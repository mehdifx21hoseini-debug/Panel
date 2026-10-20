package app.fitos.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** One module colour (data only): fill, text on fill, tinted container, text on container, text-safe ink. */
@Immutable
data class CategoryColor(
    val fill: Color,
    val onFill: Color,
    val container: Color,
    val onContainer: Color,
    val ink: Color,
)

/**
 * Colour roles of the "Electric Blue" design system (Material 3 names + FitOS extensions).
 * Every fill ships its on-* partner; all pairs were checked against WCAG (≥4.5:1 text, ≥3:1 UI).
 */
@Immutable
data class FitColors(
    val isDark: Boolean,
    val background: Color, val onBackground: Color,
    val surface: Color, val onSurface: Color,
    val surfaceDim: Color, val surfaceBright: Color,
    val surfaceContainerLowest: Color, val surfaceContainerLow: Color, val surfaceContainer: Color,
    val surfaceContainerHigh: Color, val surfaceContainerHighest: Color,
    val surfaceVariant: Color, val onSurfaceVariant: Color,
    val subtle: Color,
    val outline: Color, val outlineVariant: Color, val hairline: Color,
    val inverseSurface: Color, val inverseOnSurface: Color, val inversePrimary: Color,
    val scrim: Color,
    val primary: Color, val onPrimary: Color, val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color, val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color, val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color, val onError: Color, val errorContainer: Color, val onErrorContainer: Color,
    val heroBand: Color, val onHeroBand: Color, val onHeroBandVariant: Color,
    val training: CategoryColor, val calories: CategoryColor, val protein: CategoryColor,
    val water: CategoryColor, val sleep: CategoryColor, val habits: CategoryColor,
    val track: Color, val steel: Color, val steelDark: Color,
    val muscleBody: Color, val muscle: Color,
    val focusRing: Color,
)

val LightFitColors = FitColors(
    isDark = false,
    background = Color(0xFFF2F5FB), onBackground = Color(0xFF0F1A33),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0F1A33),
    surfaceDim = Color(0xFFE4EAF5), surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F9FD), surfaceContainer = Color(0xFFEEF2F9),
    surfaceContainerHigh = Color(0xFFE6ECF6), surfaceContainerHighest = Color(0xFFDEE5F1),
    surfaceVariant = Color(0xFFE6ECF6), onSurfaceVariant = Color(0xFF45516B),
    subtle = Color(0xFF5A6680),
    outline = Color(0xFF8390A8), outlineVariant = Color(0xFFD8DFEB), hairline = Color(0x140F1A33),
    inverseSurface = Color(0xFF1B2A4A), inverseOnSurface = Color(0xFFF2F5FB), inversePrimary = Color(0xFF9DB8FF),
    scrim = Color(0x730B1530),
    primary = Color(0xFF1F5AF5), onPrimary = Color(0xFFFFFFFF), primaryContainer = Color(0xFFE3EBFF), onPrimaryContainer = Color(0xFF0B2A80),
    secondary = Color(0xFF45516B), onSecondary = Color(0xFFFFFFFF), secondaryContainer = Color(0xFFE8EEF9), onSecondaryContainer = Color(0xFF1B2A4A),
    tertiary = Color(0xFFFF7A1A), onTertiary = Color(0xFF2B1400), tertiaryContainer = Color(0xFFFFEBDD), onTertiaryContainer = Color(0xFF7A3300),
    error = Color(0xFFB42318), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFEE4E2), onErrorContainer = Color(0xFF7A1A12),
    heroBand = Color(0xFF1F5AF5), onHeroBand = Color(0xFFFFFFFF), onHeroBandVariant = Color(0xFFE8EEFF),
    training = CategoryColor(Color(0xFFFF7A1A), Color(0xFF2B1400), Color(0xFFFFEBDD), Color(0xFF7A3300), Color(0xFFB8460A)),
    calories = CategoryColor(Color(0xFFD63A3F), Color(0xFFFFFFFF), Color(0xFFFDE7E8), Color(0xFF7A1A1D), Color(0xFFB42328)),
    protein = CategoryColor(Color(0xFFF5A400), Color(0xFF2A1C00), Color(0xFFFFF3D6), Color(0xFF5C4100), Color(0xFF7A5600)),
    water = CategoryColor(Color(0xFF12A4E8), Color(0xFF00243A), Color(0xFFDDF2FC), Color(0xFF0A4C70), Color(0xFF0B6E9E)),
    sleep = CategoryColor(Color(0xFF6A4DF5), Color(0xFFFFFFFF), Color(0xFFEEEAFF), Color(0xFF3A2A99), Color(0xFF5A3FE0)),
    habits = CategoryColor(Color(0xFF13803F), Color(0xFFFFFFFF), Color(0xFFDFF6E8), Color(0xFF0B5C2E), Color(0xFF0F7A3D)),
    track = Color(0xFFE6ECF6), steel = Color(0xFFC9D3E6), steelDark = Color(0xFF9AA7C2),
    muscleBody = Color(0xFFDEE5F1), muscle = Color(0xFFC6D1E4),
    focusRing = Color(0xFF1F5AF5),
)

val DarkFitColors = FitColors(
    isDark = true,
    background = Color(0xFF0B1530), onBackground = Color(0xFFEEF3FF),
    surface = Color(0xFF121E3D), onSurface = Color(0xFFEEF3FF),
    surfaceDim = Color(0xFF0B1530), surfaceBright = Color(0xFF233361),
    surfaceContainerLowest = Color(0xFF08102A), surfaceContainerLow = Color(0xFF0F1A36), surfaceContainer = Color(0xFF16224A),
    surfaceContainerHigh = Color(0xFF1C2A55), surfaceContainerHighest = Color(0xFF233361),
    surfaceVariant = Color(0xFF1C2A55), onSurfaceVariant = Color(0xFFB3C0DD),
    subtle = Color(0xFF93A2C4),
    outline = Color(0xFF64739A), outlineVariant = Color(0xFF26355E), hairline = Color(0x14FFFFFF),
    inverseSurface = Color(0xFFE6ECF8), inverseOnSurface = Color(0xFF0F1A33), inversePrimary = Color(0xFF1A4FD6),
    scrim = Color(0x9E030816),
    primary = Color(0xFF6F98FF), onPrimary = Color(0xFF071232), primaryContainer = Color(0xFF1D3A8C), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB3C0DD), onSecondary = Color(0xFF0B1530), secondaryContainer = Color(0xFF1F2E58), onSecondaryContainer = Color(0xFFE3EAFF),
    tertiary = Color(0xFFFF9A4D), onTertiary = Color(0xFF2B1400), tertiaryContainer = Color(0xFF3D2414), onTertiaryContainer = Color(0xFFFFD2B3),
    error = Color(0xFFFF8A80), onError = Color(0xFF2A0805), errorContainer = Color(0xFF3A1F1D), onErrorContainer = Color(0xFFFFC2BA),
    heroBand = Color(0xFF1D3A8C), onHeroBand = Color(0xFFFFFFFF), onHeroBandVariant = Color(0xFFD6E2FF),
    training = CategoryColor(Color(0xFFFF9A4D), Color(0xFF2B1400), Color(0xFF3D2414), Color(0xFFFFD2B3), Color(0xFFFFB07A)),
    calories = CategoryColor(Color(0xFFFF6B6F), Color(0xFF2A0A0B), Color(0xFF3D1A1D), Color(0xFFFFC9CB), Color(0xFFFF8E91)),
    protein = CategoryColor(Color(0xFFFFC233), Color(0xFF2A1C00), Color(0xFF3A3015), Color(0xFFFFE29A), Color(0xFFFFD166)),
    water = CategoryColor(Color(0xFF4CC3F5), Color(0xFF00243A), Color(0xFF0E3247), Color(0xFFBDE9FC), Color(0xFF7AD3F8)),
    sleep = CategoryColor(Color(0xFF9E8BFF), Color(0xFF120E33), Color(0xFF262050), Color(0xFFDAD3FF), Color(0xFFB6A7FF)),
    habits = CategoryColor(Color(0xFF3DDC84), Color(0xFF03251A), Color(0xFF123A26), Color(0xFFA9F2C8), Color(0xFF5FE39A)),
    track = Color(0xFF22305A), steel = Color(0xFF34446E), steelDark = Color(0xFF4A5B88),
    muscleBody = Color(0xFF233361), muscle = Color(0xFF31437A),
    focusRing = Color(0xFF6F98FF),
)

/** Maps the FitOS roles onto Material 3 so stock M3 components follow the theme. */
internal fun FitColors.toMaterialScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
        error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        outline = outline, outlineVariant = outlineVariant, scrim = scrim,
        surfaceBright = surfaceBright, surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest, surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
        error = error, onError = onError, errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        outline = outline, outlineVariant = outlineVariant, scrim = scrim,
        surfaceBright = surfaceBright, surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest, surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
    )
}
