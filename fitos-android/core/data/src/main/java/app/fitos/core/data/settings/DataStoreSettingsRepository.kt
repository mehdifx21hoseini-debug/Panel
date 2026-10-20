package app.fitos.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.fitos.core.domain.settings.AutoBackupError
import app.fitos.core.domain.settings.AutoBackupSettings
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.core.domain.training.ReturnPlan
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

    override val autoBackup: Flow<AutoBackupSettings> = store.data.map { it.autoBackup() }

    override suspend fun updateAutoBackup(change: (AutoBackupSettings) -> AutoBackupSettings) {
        store.edit { prefs ->
            val next = change(prefs.autoBackup())
            prefs[AUTO_ENABLED] = next.enabled
            prefs.setOrRemove(AUTO_FOLDER, next.folder)
            prefs.setOrRemove(AUTO_FOLDER_NAME, next.folderName)
            prefs[AUTO_ENCRYPTED] = next.encrypted
            prefs[AUTO_KEEP] = next.keep
            prefs.setOrRemove(AUTO_LAST_RUN, next.lastRunAt?.toEpochMilli())
            prefs.setOrRemove(AUTO_LAST_ERROR, next.lastError?.name)
        }
    }

    override val returnThresholdDays: Flow<Int> = store.data.map { it[RETURN_DAYS] ?: ReturnPlan.DEFAULT_THRESHOLD_DAYS }

    override suspend fun setReturnThresholdDays(days: Int) {
        store.edit { it[RETURN_DAYS] = days.coerceIn(ReturnPlan.THRESHOLD_DAYS) }
    }

    override val returnNotify: Flow<Boolean> = store.data.map { it[RETURN_NOTIFY] ?: true }

    override suspend fun setReturnNotify(on: Boolean) {
        store.edit { it[RETURN_NOTIFY] = on }
    }

    private fun Preferences.autoBackup() = AutoBackupSettings(
        enabled = this[AUTO_ENABLED] ?: false,
        folder = this[AUTO_FOLDER],
        folderName = this[AUTO_FOLDER_NAME],
        encrypted = this[AUTO_ENCRYPTED] ?: false,
        keep = this[AUTO_KEEP] ?: AutoBackupSettings.DEFAULT_KEEP,
        lastRunAt = this[AUTO_LAST_RUN]?.let(Instant::ofEpochMilli),
        lastError = this[AUTO_LAST_ERROR]?.let { name -> AutoBackupError.entries.firstOrNull { it.name == name } },
    )

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val AUTO_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_FOLDER = stringPreferencesKey("auto_backup_folder")
        val AUTO_FOLDER_NAME = stringPreferencesKey("auto_backup_folder_name")
        val AUTO_ENCRYPTED = booleanPreferencesKey("auto_backup_encrypted")
        val AUTO_KEEP = intPreferencesKey("auto_backup_keep")
        val AUTO_LAST_RUN = longPreferencesKey("auto_backup_last_run")
        val AUTO_LAST_ERROR = stringPreferencesKey("auto_backup_last_error")
        val RETURN_DAYS = intPreferencesKey("return_threshold_days")
        val RETURN_NOTIFY = booleanPreferencesKey("return_notify")
    }
}
