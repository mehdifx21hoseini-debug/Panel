package app.fitos.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.settingsStore

    override val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.LIGHT
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
