package app.fitos.personal.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitos.core.domain.backup.BackupError
import app.fitos.core.domain.backup.BackupException
import app.fitos.core.domain.backup.BackupService
import app.fitos.core.domain.backup.SafetyBackup
import app.fitos.core.domain.backup.StagedBackup
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.time.JalaliDate
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PasswordProblem { TOO_SHORT, MISMATCH }

data class BackupUiState(
    val encrypt: Boolean = true,
    val password: String = "",
    val repeat: String = "",
    val passwordProblem: PasswordProblem? = null,
    val exporting: Boolean = false,
    val exportedName: String? = null,
    val exportError: BackupError? = null,
    /** A picked file that needs its password before it can be checked. */
    val pendingEncrypted: (() -> InputStream)? = null,
    val restorePassword: String = "",
    val checking: Boolean = false,
    val staged: StagedBackup? = null,
    val restoreError: BackupError? = null,
    val restoring: Boolean = false,
    val restored: Boolean = false,
    val safety: List<SafetyBackup> = emptyList(),
)

/**
 * Drives the backup screen. Files come in and go out as stream openers built from the
 * system file picker, so this class never touches Android storage APIs itself.
 */
class BackupViewModel(
    private val backup: BackupService,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    val lastBackupAt: StateFlow<Instant?> = settings.lastBackupAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refreshSafety()
    }

    /** "FitOS-1405-07-28.fitbak": Jalali date, Latin digits so every file manager shows it right. */
    fun suggestedFileName(): String {
        val j = JalaliDate.of(LocalDate.now(clock))
        return "FitOS-%04d-%02d-%02d.fitbak".format(j.year, j.month, j.day)
    }

    fun setEncrypt(on: Boolean) = _state.update { it.copy(encrypt = on, passwordProblem = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, passwordProblem = null) }
    fun setRepeat(value: String) = _state.update { it.copy(repeat = value, passwordProblem = null) }
    fun setRestorePassword(value: String) = _state.update { it.copy(restorePassword = value, restoreError = null) }

    /** Checks the password fields; true when the file picker may open. */
    fun readyToExport(): Boolean {
        val s = _state.value
        val problem = when {
            !s.encrypt -> null
            s.password.length < MIN_PASSWORD -> PasswordProblem.TOO_SHORT
            s.password != s.repeat -> PasswordProblem.MISMATCH
            else -> null
        }
        _state.update { it.copy(passwordProblem = problem, exportedName = null, exportError = null) }
        return problem == null
    }

    fun export(open: () -> OutputStream, displayName: String) {
        val s = _state.value
        _state.update { it.copy(exporting = true, exportedName = null, exportError = null) }
        viewModelScope.launch {
            try {
                open().use { out -> backup.export(out, if (s.encrypt) s.password.toCharArray() else null) }
                _state.update { it.copy(exporting = false, exportedName = displayName, password = "", repeat = "") }
            } catch (e: BackupException) {
                _state.update { it.copy(exporting = false, exportError = e.error) }
            } catch (e: Exception) {
                _state.update { it.copy(exporting = false, exportError = BackupError.IO) }
            }
        }
    }

    /** A file was picked: ask for its password if it has one, otherwise check it right away. */
    fun pick(open: () -> InputStream) {
        _state.update { it.copy(staged = null, restoreError = null, restored = false, pendingEncrypted = null, restorePassword = "") }
        viewModelScope.launch {
            val encrypted = try {
                backup.isEncrypted(open())
            } catch (e: Exception) {
                _state.update { it.copy(restoreError = BackupError.IO) }
                return@launch
            }
            if (encrypted) _state.update { it.copy(pendingEncrypted = open) } else stage(open, null)
        }
    }

    fun unlock() {
        val open = _state.value.pendingEncrypted ?: return
        stage(open, _state.value.restorePassword.toCharArray())
    }

    fun stageSafety(item: SafetyBackup) {
        _state.update { it.copy(staged = null, restoreError = null, restored = false, pendingEncrypted = null, checking = true) }
        viewModelScope.launch {
            try {
                val staged = backup.stageSafetyBackup(item.id)
                _state.update { it.copy(checking = false, staged = staged) }
            } catch (e: BackupException) {
                _state.update { it.copy(checking = false, restoreError = e.error) }
            }
        }
    }

    private fun stage(open: () -> InputStream, password: CharArray?) {
        _state.update { it.copy(checking = true, restoreError = null) }
        viewModelScope.launch {
            try {
                val staged = open().use { backup.stage(it, password) }
                _state.update { it.copy(checking = false, staged = staged, pendingEncrypted = null, restorePassword = "") }
            } catch (e: BackupException) {
                _state.update { it.copy(checking = false, restoreError = e.error) }
            } catch (e: Exception) {
                _state.update { it.copy(checking = false, restoreError = BackupError.IO) }
            }
        }
    }

    fun restore() {
        val staged = _state.value.staged ?: return
        _state.update { it.copy(restoring = true, restoreError = null) }
        viewModelScope.launch {
            try {
                backup.restore(staged)
                _state.update { it.copy(restoring = false, restored = true, staged = null) }
                refreshSafety()
            } catch (e: BackupException) {
                _state.update { it.copy(restoring = false, restoreError = e.error) }
            }
        }
    }

    fun cancelStaged() = _state.update { it.copy(staged = null, pendingEncrypted = null, restorePassword = "") }

    private fun refreshSafety() {
        viewModelScope.launch { _state.update { it.copy(safety = backup.safetyBackups()) } }
    }

    companion object {
        const val MIN_PASSWORD = 8
    }
}
