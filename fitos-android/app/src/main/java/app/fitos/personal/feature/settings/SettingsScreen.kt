package app.fitos.personal.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.BuildConfig
import app.fitos.personal.R
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.LIGHT)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    }

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.LIGHT -> FitIcons.Sun
        ThemeMode.DARK -> FitIcons.Moon
        ThemeMode.SYSTEM -> FitIcons.Settings
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(settingsRepository) } })
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colors = FitTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(FitIcons.Back, contentDescription = stringResource(R.string.settings_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
        ) {
            SectionLabel(R.string.settings_appearance)
            FitCard {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = FitSpacing.target)
                                .selectable(selected = option == mode, role = Role.RadioButton, onClick = { viewModel.setThemeMode(option) }),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
                        ) {
                            Icon(option.icon, contentDescription = null, tint = colors.onSurfaceVariant)
                            Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            RadioButton(
                                selected = option == mode,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.outline),
                            )
                        }
                    }
                }
            }

            SectionLabel(R.string.settings_privacy)
            FitCard {
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                    EmojiTile(FitEmoji.LOCK, tint = colors.habits.container)
                    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                        Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }

            SectionLabel(R.string.settings_about)
            FitCard {
                Text(
                    stringResource(R.string.settings_version, PersianNumbers.digits(BuildConfig.VERSION_NAME)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                HorizontalDivider(color = colors.outlineVariant)
                Text(stringResource(R.string.settings_licences), style = MaterialTheme.typography.titleSmall)
                listOf(R.string.licence_vazirmatn, R.string.licence_changa, R.string.licence_fluent).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = FitTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(top = FitSpacing.s8, start = FitSpacing.s4).semantics { heading() },
    )
}
