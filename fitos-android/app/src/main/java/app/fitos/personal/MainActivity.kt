package app.fitos.personal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.ui.FitOsRoot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainActivity : ComponentActivity() {

    private val container get() = (application as FitOsApplication).container
    private val appViewModel: AppViewModel by viewModels { AppViewModel.factory(container.settingsRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until the saved theme is known, so a dark-theme user never sees a white flash.
        splash.setKeepOnScreenCondition { appViewModel.themeMode.value == null }
        enableEdgeToEdge()
        setContent {
            val mode = appViewModel.themeMode.collectAsStateWithLifecycle().value
            if (mode != null) {
                FitOsRoot(themeMode = mode, settingsRepository = container.settingsRepository, clock = container.clock)
            }
        }
    }
}

/** Holds the app-wide theme choice; null until DataStore has answered. */
class AppViewModel(settings: SettingsRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode?> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        fun factory(settings: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(settings) }
        }
    }
}
