package app.fitos.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import java.time.Instant
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

    override val lastBackupAt: Flow<Instant?> = store.data.map { prefs -> prefs[LAST_BACKUP_AT]?.let(Instant::ofEpochMilli) }

    override suspend fun setLastBackupAt(at: Instant) {
        store.edit { it[LAST_BACKUP_AT] = at.toEpochMilli() }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }
}
