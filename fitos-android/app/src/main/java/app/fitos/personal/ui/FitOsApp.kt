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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.core.domain.training.VariantKind
import app.fitos.personal.AppContainer
import app.fitos.personal.R
import app.fitos.personal.feature.backup.BackupScreen
import app.fitos.personal.feature.backup.BackupViewModel
import app.fitos.personal.feature.body.BodyScreen
import app.fitos.personal.feature.body.BodyViewModel
import app.fitos.personal.feature.body.MeasurementsEntryScreen
import app.fitos.personal.feature.body.MeasurementsViewModel
import app.fitos.personal.feature.body.SiteHistoryScreen
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
import app.fitos.personal.feature.training.ChangeLogScreen
import app.fitos.personal.feature.training.ChangeLogViewModel
import app.fitos.personal.feature.training.DayEditorScreen
import app.fitos.personal.feature.training.DayEditorViewModel
import app.fitos.personal.feature.training.ExerciseDetailScreen
import app.fitos.personal.feature.training.ExerciseDetailViewModel
import app.fitos.personal.feature.training.ExerciseFormScreen
import app.fitos.personal.feature.training.ExerciseFormViewModel
import app.fitos.personal.feature.training.LibraryMode
import app.fitos.personal.feature.training.LibraryScreen
import app.fitos.personal.feature.training.LibraryViewModel
import app.fitos.personal.feature.training.ProgramEditorScreen
import app.fitos.personal.feature.training.ProgramEditorViewModel
import app.fitos.personal.feature.training.ProgramsScreen
import app.fitos.personal.feature.training.ProgramsViewModel
import app.fitos.personal.feature.training.TrainingScreen
import app.fitos.personal.feature.training.TrainingViewModel
import java.time.LocalDate
import java.time.LocalTime
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
@Serializable data object MeasurementsEntryRoute
@Serializable data class SiteHistoryRoute(val siteId: String)
@Serializable data class BackupRoute(val restoreOnly: Boolean = false)
@Serializable data object ProgramsRoute
@Serializable data class ProgramEditorRoute(val programId: String)
@Serializable data class DayEditorRoute(val programId: String, val dayId: String)
@Serializable data class ChangeLogRoute(val programId: String)
/** [mode] is a [LibraryMode] name; [kind] a [VariantKind] code (pick mode); [plannedId] for replace mode. */
@Serializable data class LibraryRoute(val mode: String = LibraryMode.BROWSE.name, val dayId: String? = null, val kind: String? = null, val plannedId: String? = null)
@Serializable data class ExerciseDetailRoute(val exerciseId: String)
@Serializable data class ExerciseFormRoute(val exerciseId: String? = null)

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
                    onRestore = { navController.navigate(BackupRoute(restoreOnly = true)) },
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = viewModel { HomeViewModel(data.profiles, data.targets, data.weights, data.goals, data.programs) },
                    today = today,
                    now = { LocalTime.now(clock) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenTargets = { navController.navigate(TargetsRoute) },
                    onOpenWorkout = { programId, dayId -> navController.navigate(DayEditorRoute(programId, dayId)) },
                    onOpenTraining = {
                        navController.navigate(TrainingRoute) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable<TrainingRoute> {
                TrainingScreen(
                    viewModel = viewModel { TrainingViewModel(data.programs, data.profiles, clock) },
                    onOpenProgram = { navController.navigate(ProgramEditorRoute(it)) },
                    onOpenDay = { programId, dayId -> navController.navigate(DayEditorRoute(programId, dayId)) },
                    onOpenPrograms = { navController.navigate(ProgramsRoute) },
                    onOpenLibrary = { navController.navigate(LibraryRoute()) },
                )
            }
            composable<ProgramsRoute> {
                ProgramsScreen(
                    viewModel = viewModel { ProgramsViewModel(data.programs, data.profiles) },
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(ProgramEditorRoute(it)) },
                )
            }
            composable<ProgramEditorRoute> { entry ->
                val route = entry.toRoute<ProgramEditorRoute>()
                ProgramEditorScreen(
                    viewModel = viewModel { ProgramEditorViewModel(data.programs, route.programId) },
                    onBack = { navController.popBackStack() },
                    onOpenDay = { navController.navigate(DayEditorRoute(route.programId, it)) },
                    onOpenChanges = { navController.navigate(ChangeLogRoute(route.programId)) },
                )
            }
            composable<DayEditorRoute> { entry ->
                val route = entry.toRoute<DayEditorRoute>()
                DayEditorScreen(
                    viewModel = viewModel { DayEditorViewModel(data.programs, route.programId, route.dayId) },
                    onBack = { navController.popBackStack() },
                    onAddExercise = { kind -> navController.navigate(LibraryRoute(LibraryMode.PICK.name, dayId = route.dayId, kind = kind.code)) },
                    onReplace = { navController.navigate(LibraryRoute(LibraryMode.REPLACE.name, plannedId = it)) },
                )
            }
            composable<ChangeLogRoute> { entry ->
                val route = entry.toRoute<ChangeLogRoute>()
                ChangeLogScreen(viewModel { ChangeLogViewModel(data.programs, route.programId) }, onBack = { navController.popBackStack() })
            }
            composable<LibraryRoute> { entry ->
                val route = entry.toRoute<LibraryRoute>()
                LibraryScreen(
                    viewModel = viewModel {
                        LibraryViewModel(
                            data.exercises,
                            data.programs,
                            LibraryMode.valueOf(route.mode),
                            dayId = route.dayId,
                            kind = VariantKind.fromCode(route.kind.orEmpty()),
                            plannedId = route.plannedId,
                        )
                    },
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                    onNewExercise = { navController.navigate(ExerciseFormRoute()) },
                )
            }
            composable<ExerciseDetailRoute> { entry ->
                val route = entry.toRoute<ExerciseDetailRoute>()
                ExerciseDetailScreen(
                    viewModel = viewModel { ExerciseDetailViewModel(data.exercises, route.exerciseId) },
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(ExerciseFormRoute(it)) },
                )
            }
            composable<ExerciseFormRoute> { entry ->
                val route = entry.toRoute<ExerciseFormRoute>()
                ExerciseFormScreen(
                    viewModel = viewModel { ExerciseFormViewModel(data.exercises, route.exerciseId) },
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable<NutritionRoute> {
                ComingSoonScreen(R.string.nav_nutrition, FitEmoji.SALAD, R.string.soon_nutrition_title, R.string.soon_nutrition_body)
            }
            composable<BodyRoute> {
                BodyScreen(
                    viewModel = viewModel { BodyViewModel(data.weights, data.goals, data.measurements, clock) },
                    onLogMeasurements = { navController.navigate(MeasurementsEntryRoute) },
                    onOpenSite = { navController.navigate(SiteHistoryRoute(it)) },
                    onNewGoal = { navController.navigate(ProfileEditRoute) },
                )
            }
            composable<MeasurementsEntryRoute> {
                MeasurementsEntryScreen(
                    viewModel = viewModel { MeasurementsViewModel(data.measurements, clock) },
                    onDone = { navController.popBackStack() },
                )
            }
            composable<SiteHistoryRoute> { entry ->
                SiteHistoryScreen(
                    viewModel = viewModel { MeasurementsViewModel(data.measurements, clock) },
                    siteId = entry.toRoute<SiteHistoryRoute>().siteId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<BackupRoute> { entry ->
                val route = entry.toRoute<BackupRoute>()
                BackupScreen(
                    viewModel = viewModel { BackupViewModel(data.backup, container.settingsRepository, clock) },
                    restoreOnly = route.restoreOnly,
                    onBack = { navController.popBackStack() },
                    onRestored = {
                        navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                    },
                )
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
                    onOpenBackup = { navController.navigate(BackupRoute()) },
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
