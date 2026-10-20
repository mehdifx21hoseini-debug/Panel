package app.fitos.personal.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.AppContainer
import app.fitos.personal.R
import app.fitos.personal.feature.home.HomeScreen
import app.fitos.personal.feature.home.HomeViewModel
import app.fitos.personal.feature.onboarding.OnboardingScreen
import app.fitos.personal.feature.onboarding.OnboardingViewModel
import app.fitos.personal.feature.placeholder.ComingSoonScreen
import app.fitos.personal.feature.profile.ProfileEditScreen
import app.fitos.personal.feature.profile.ProfileEditViewModel
import app.fitos.personal.feature.settings.SettingsScreen
import app.fitos.personal.feature.targets.TargetsScreen
import app.fitos.personal.feature.targets.TargetsViewModel
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object TrainingRoute
@Serializable data object NutritionRoute
@Serializable data object BodyRoute
@Serializable data object HabitsRoute
@Serializable data object SettingsRoute
@Serializable data object OnboardingRoute
@Serializable data object ProfileEditRoute
@Serializable data object TargetsRoute

/** The five tabs of the bottom navigation bar, in reading order (right to left on screen). */
private enum class TopLevel(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, @param:StringRes val label: Int) {
    Home(HomeRoute, HomeRoute::class, FitIcons.Home, R.string.nav_home),
    Training(TrainingRoute, TrainingRoute::class, FitIcons.Dumbbell, R.string.nav_training),
    Nutrition(NutritionRoute, NutritionRoute::class, FitIcons.Utensils, R.string.nav_nutrition),
    Body(BodyRoute, BodyRoute::class, FitIcons.Body, R.string.nav_body),
    Habits(HabitsRoute, HabitsRoute::class, FitIcons.Habits, R.string.nav_habits),
}

/**
 * Theme + app shell. Separate from the activity so tests can run it with an in-memory database
 * and a fixed clock. [startWithSetup] opens first-run setup instead of Home.
 */
@Composable
fun FitOsRoot(themeMode: ThemeMode, container: AppContainer, startWithSetup: Boolean) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    FitTheme(darkTheme = dark) {
        FitOsApp(container, startWithSetup)
    }
}

@Composable
fun FitOsApp(container: AppContainer, startWithSetup: Boolean) {
    val data = container.data
    val clock = container.clock
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = TopLevel.entries.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }

    // "Today" is re-read on every resume so the date is right after midnight.
    var today by remember { mutableStateOf(LocalDate.now(clock)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now(clock) }

    SystemBarIcons(lightContent = FitTheme.colors.isDark || currentTab == TopLevel.Home || destination == null)

    Scaffold(
        containerColor = FitTheme.colors.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentTab != null) {
                FitNavigationBar(currentTab) { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = if (startWithSetup) OnboardingRoute else HomeRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    viewModel = viewModel { OnboardingViewModel(data.setup, clock) },
                    onFinished = {
                        navController.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                    },
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = viewModel { HomeViewModel(data.profiles, data.targets, data.weights, data.goals) },
                    today = today,
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenTargets = { navController.navigate(TargetsRoute) },
                )
            }
            composable<TrainingRoute> {
                ComingSoonScreen(R.string.nav_training, FitEmoji.LIFTING, R.string.soon_training_title, R.string.soon_training_body)
            }
            composable<NutritionRoute> {
                ComingSoonScreen(R.string.nav_nutrition, FitEmoji.SALAD, R.string.soon_nutrition_title, R.string.soon_nutrition_body)
            }
            composable<BodyRoute> {
                ComingSoonScreen(R.string.nav_body, FitEmoji.SCALE, R.string.soon_body_title, R.string.soon_body_body)
            }
            composable<HabitsRoute> {
                ComingSoonScreen(R.string.nav_habits, FitEmoji.SPARKLES, R.string.soon_habits_title, R.string.soon_habits_body)
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    settingsRepository = container.settingsRepository,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(ProfileEditRoute) },
                    onOpenTargets = { navController.navigate(TargetsRoute) },
                )
            }
            composable<ProfileEditRoute> {
                ProfileEditScreen(
                    viewModel = viewModel { ProfileEditViewModel(data.profiles, data.goals, data.weights, clock) },
                    onDone = { navController.popBackStack() },
                )
            }
            composable<TargetsRoute> {
                TargetsScreen(
                    viewModel = viewModel { TargetsViewModel(data.targets, data.profiles, data.weights, data.goals, clock) },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun FitNavigationBar(current: TopLevel, onSelect: (TopLevel) -> Unit) {
    val colors = FitTheme.colors
    NavigationBar(containerColor = colors.surfaceContainer) {
        TopLevel.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { if (tab != current) onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimary,
                    indicatorColor = colors.primary,
                    selectedTextColor = colors.onSurface,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}

/** White status-bar icons over the blue band and in dark theme; dark icons on light screens. */
@Composable
private fun SystemBarIcons(lightContent: Boolean) {
    val view = LocalView.current
    val darkTheme = FitTheme.colors.isDark
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightContent
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
