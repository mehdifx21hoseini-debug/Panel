package app.fitos.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val LocalFitColors = staticCompositionLocalOf { LightFitColors }

/**
 * Root theme: Electric Blue colours (light by default), Vazirmatn/Changa type, the shape scale,
 * and a right-to-left layout for the whole app regardless of the device language.
 */
@Composable
fun FitTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkFitColors else LightFitColors
    val scheme = remember(colors) { colors.toMaterialScheme() }
    CompositionLocalProvider(
        LocalFitColors provides colors,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = scheme, typography = FitTypography, shapes = FitShapes, content = content)
    }
}

object FitTheme {
    val colors: FitColors
        @Composable @ReadOnlyComposable get() = LocalFitColors.current
}
