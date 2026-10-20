package app.fitos.personal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.fitos.core.domain.profile.SetupRepository
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.notify.Destination
import app.fitos.personal.notify.Notifications
import app.fitos.personal.ui.FitOsRoot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {

    private val container get() = (application as FitOsApplication).container
    private val appViewModel: AppViewModel by viewModels {
        AppViewModel.factory(container.settingsRepository, container.data.setup)
    }

    /** Where a tapped notification asked to go, until the app has gone there. */
    private val open = MutableStateFlow<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) open.value = Notifications.destinationOf(intent)
        // Keep the splash until the saved theme and the setup state are known: no white flash,
        // and no glimpse of Home before first-run setup.
        splash.setKeepOnScreenCondition { appViewModel.launch.value == null }
        enableEdgeToEdge()
        setContent {
            val launch = appViewModel.launch.collectAsStateWithLifecycle().value
            if (launch != null) {
                val startWithSetup = remember { launch.needsSetup }
                val destination by open.collectAsStateWithLifecycle()
                FitOsRoot(
                    themeMode = launch.themeMode, container = container, startWithSetup = startWithSetup,
                    open = destination, onOpened = { open.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Notifications.destinationOf(intent)?.let { open.value = it }
    }
}

data class LaunchState(val themeMode: ThemeMode, val needsSetup: Boolean)

/** App-wide state needed before the first frame; null until DataStore and the database have answered. */
class AppViewModel(settings: SettingsRepository, setup: SetupRepository) : ViewModel() {
    val launch: StateFlow<LaunchState?> = combine(settings.themeMode, setup.isSetupComplete) { mode, complete ->
        LaunchState(mode, needsSetup = !complete)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        fun factory(settings: SettingsRepository, setup: SetupRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(settings, setup) }
        }
    }
}
