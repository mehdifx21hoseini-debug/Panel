package app.fitos.personal.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.data.backup.AutoBackupRunner
import app.fitos.core.data.backup.SecretStore
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitStepper
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.settings.AutoBackupError
import app.fitos.core.domain.settings.AutoBackupSettings
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.time.DateFormats
import app.fitos.personal.R
import app.fitos.personal.notify.BackgroundJobs
import app.fitos.personal.ui.Fmt
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What happened to the last "back up now". */
sealed interface RunResult {
    data object Running : RunResult
    data object Done : RunResult
    data class Failed(val error: AutoBackupError) : RunResult
}

class AutoBackupViewModel(
    private val settings: SettingsRepository,
    private val runner: AutoBackupRunner,
    private val secrets: SecretStore,
    private val jobs: BackgroundJobs,
) : ViewModel() {
    val state: StateFlow<AutoBackupSettings> = settings.autoBackup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutoBackupSettings())

    private val _run = MutableStateFlow<RunResult?>(null)
    val run: StateFlow<RunResult?> = _run.asStateFlow()

    fun pickFolder(uri: String, name: String) = viewModelScope.launch {
        settings.updateAutoBackup { it.copy(folder = uri, folderName = name, lastError = null) }
    }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        settings.updateAutoBackup { it.copy(enabled = on) }
        jobs.autoBackupChanged(on)
    }

    fun setKeep(keep: Int) = viewModelScope.launch {
        settings.updateAutoBackup { it.copy(keep = keep.coerceIn(AutoBackupSettings.KEEP)) }
    }

    /** Null turns the password off. */
    fun setPassword(password: CharArray?) = viewModelScope.launch {
        secrets.put(password)
        settings.updateAutoBackup { it.copy(encrypted = password != null, lastError = if (it.lastError == AutoBackupError.PASSWORD) null else it.lastError) }
    }

    fun runNow() {
        _run.value = RunResult.Running
        viewModelScope.launch {
            val error = runner.run(force = true)
            _run.value = if (error == null) RunResult.Done else RunResult.Failed(error)
        }
    }
}

@Composable
fun AutoBackupError.message(): String = stringResource(
    when (this) {
        AutoBackupError.FOLDER -> R.string.auto_backup_error_folder
        AutoBackupError.WRITE -> R.string.auto_backup_error_write
        AutoBackupError.PASSWORD -> R.string.auto_backup_error_password
    },
)

/** Weekly backup into a folder the user picked; the system keeps the app's access to that folder. */
@Composable
fun AutoBackupSection(viewModel: AutoBackupViewModel) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = FitTheme.colors
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var settingPassword by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<PasswordProblem?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            // Let go of the previous folder.
            s.folder?.takeIf { it != uri.toString() }?.let { old -> runCatching { context.contentResolver.releasePersistableUriPermission(old.toUri(), flags) } }
            viewModel.pickFolder(uri.toString(), folderName(context, uri))
        }
    }

    FitCard {
        Text(stringResource(R.string.auto_backup_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(
            s.folderName?.let { stringResource(R.string.auto_backup_folder, it) } ?: stringResource(R.string.auto_backup_folder_none),
            style = MaterialTheme.typography.titleSmall,
        )
        FitSecondaryButton(
            stringResource(if (s.folder == null) R.string.auto_backup_pick else R.string.auto_backup_change),
            onClick = { pickFolder.launch(null) },
            icon = FitIcons.Download,
            modifier = Modifier.fillMaxWidth(),
        )
        FitNote(stringResource(R.string.auto_backup_cloud_note))
        HorizontalDivider(color = colors.outlineVariant)
        SwitchRow(stringResource(R.string.auto_backup_switch), s.enabled, enabled = s.folder != null) { viewModel.setEnabled(it) }
        FitStepper(
            label = stringResource(R.string.auto_backup_keep),
            valueText = Fmt.int(s.keep),
            onDecrease = { viewModel.setKeep(s.keep - 1) },
            onIncrease = { viewModel.setKeep(s.keep + 1) },
            canDecrease = s.keep > AutoBackupSettings.KEEP.first,
            canIncrease = s.keep < AutoBackupSettings.KEEP.last,
            decreaseLabel = stringResource(R.string.action_decrease),
            increaseLabel = stringResource(R.string.action_increase),
        )
        HorizontalDivider(color = colors.outlineVariant)
        SwitchRow(stringResource(R.string.auto_backup_password_switch), s.encrypted || settingPassword) { on ->
            if (on) {
                settingPassword = true
            } else {
                settingPassword = false
                viewModel.setPassword(null)
            }
        }
        if (s.encrypted && !settingPassword) Text(stringResource(R.string.auto_backup_password_saved), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        if (settingPassword) {
            Text(stringResource(R.string.auto_backup_password_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            PasswordField(password, { password = it; problem = null }, stringResource(R.string.backup_password),
                error = if (problem == PasswordProblem.TOO_SHORT) stringResource(R.string.backup_password_short) else null)
            PasswordField(repeat, { repeat = it; problem = null }, stringResource(R.string.backup_password_repeat),
                error = if (problem == PasswordProblem.MISMATCH) stringResource(R.string.backup_password_mismatch) else null)
            FitNote(stringResource(R.string.backup_password_warning), warning = true)
            FitSecondaryButton(
                stringResource(R.string.auto_backup_password_save),
                onClick = {
                    problem = when {
                        password.length < BackupViewModel.MIN_PASSWORD -> PasswordProblem.TOO_SHORT
                        password != repeat -> PasswordProblem.MISMATCH
                        else -> null
                    }
                    if (problem == null) {
                        viewModel.setPassword(password.toCharArray())
                        password = ""
                        repeat = ""
                        settingPassword = false
                    }
                },
                icon = FitIcons.Lock,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider(color = colors.outlineVariant)
        Text(
            s.lastRunAt?.let { at -> stringResource(R.string.auto_backup_last, DateFormats.longDate(at.atZone(ZoneId.systemDefault()).toLocalDate())) }
                ?: stringResource(R.string.auto_backup_never),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        when (val r = run) {
            RunResult.Running -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.track)
            RunResult.Done -> FitNote(stringResource(R.string.auto_backup_done))
            is RunResult.Failed -> FitNote(r.error.message(), warning = true)
            null -> s.lastError?.let { FitNote(it.message(), warning = true) }
        }
        FitSecondaryButton(
            stringResource(R.string.auto_backup_now),
            onClick = viewModel::runNow,
            enabled = s.folder != null && run != RunResult.Running,
            icon = FitIcons.Refresh,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = FitTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = FitSpacing.target), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleSmall, color = if (enabled) colors.onSurface else colors.subtle) }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

/** The folder's own name ("FitOS" rather than a content URI). */
private fun folderName(context: Context, tree: Uri): String = runCatching {
    val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: tree.lastPathSegment.orEmpty()
