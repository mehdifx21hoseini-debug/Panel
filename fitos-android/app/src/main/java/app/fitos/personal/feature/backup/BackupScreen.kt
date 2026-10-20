package app.fitos.personal.feature.backup

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.backup.BackupError
import app.fitos.core.domain.backup.BackupSummary
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.time.DateFormats
import app.fitos.personal.R
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import java.time.Instant
import java.time.ZoneId

@Composable
fun BackupError.message(): String = stringResource(
    when (this) {
        BackupError.NOT_A_BACKUP -> R.string.backup_error_not_backup
        BackupError.PASSWORD_NEEDED -> R.string.backup_error_password_needed
        BackupError.WRONG_PASSWORD_OR_DAMAGED -> R.string.backup_error_wrong_password
        BackupError.DAMAGED -> R.string.backup_error_damaged
        BackupError.NEWER_APP -> R.string.backup_error_newer
        BackupError.IO -> R.string.backup_error_io
    },
)

private fun dateTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
    val local = at.atZone(zone)
    return DateFormats.longDate(local.toLocalDate()) to PersianNumbers.digits("%02d:%02d".format(local.hour, local.minute))
}

/**
 * Backup and restore. [restoreOnly] is used from the welcome page, where there is nothing to back
 * up yet; [onRestored] then continues into the app.
 */
@Composable
fun BackupScreen(viewModel: BackupViewModel, restoreOnly: Boolean, onBack: () -> Unit, onRestored: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lastBackup by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver
    val colors = FitTheme.colors
    var confirming by remember { mutableStateOf(false) }

    fun nameOf(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: uri.lastPathSegment.orEmpty()

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.export({ resolver.openOutputStream(uri, "wt") ?: error("no stream") }, nameOf(uri))
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pick { resolver.openInputStream(uri) ?: error("no stream") }
    }

    EditScaffold(title = stringResource(R.string.backup_title), onBack = onBack) {
        FitNote(stringResource(R.string.backup_intro))

        if (!restoreOnly) {
            SectionTitle(stringResource(R.string.backup_create_title))
            FitCard {
                Text(
                    lastBackup?.let { stringResource(R.string.backup_last, dateTime(it).first) } ?: stringResource(R.string.backup_never),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = FitSpacing.target)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.backup_encrypt), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.backup_encrypt_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Switch(checked = state.encrypt, onCheckedChange = viewModel::setEncrypt, colors = SwitchDefaults.colors(checkedTrackColor = colors.primary))
                }
                if (state.encrypt) {
                    PasswordField(state.password, viewModel::setPassword, stringResource(R.string.backup_password),
                        error = if (state.passwordProblem == PasswordProblem.TOO_SHORT) stringResource(R.string.backup_password_short) else null)
                    PasswordField(state.repeat, viewModel::setRepeat, stringResource(R.string.backup_password_repeat),
                        error = if (state.passwordProblem == PasswordProblem.MISMATCH) stringResource(R.string.backup_password_mismatch) else null)
                    FitNote(stringResource(R.string.backup_password_warning), warning = true)
                }
                if (state.exporting) {
                    Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.track)
                }
                state.exportedName?.let { FitNote(stringResource(R.string.backup_done, it)) }
                state.exportError?.let { FitNote(it.message(), warning = true) }
                FitPrimaryButton(
                    stringResource(R.string.backup_create),
                    onClick = { if (viewModel.readyToExport()) createFile.launch(viewModel.suggestedFileName()) },
                    enabled = !state.exporting,
                    icon = FitIcons.Download,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionTitle(stringResource(R.string.restore_title))
        FitCard {
            when {
                state.restored -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(FitEmoji.CHECK, tint = colors.habits.container)
                        Text(stringResource(R.string.restore_done), style = MaterialTheme.typography.titleMedium)
                    }
                    if (restoreOnly) FitPrimaryButton(stringResource(R.string.restore_go_home), onClick = onRestored, modifier = Modifier.fillMaxWidth())
                }
                state.staged != null -> {
                    val staged = state.staged!!
                    Preview(staged.summary)
                    Button(
                        onClick = { confirming = true },
                        enabled = !state.restoring,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text(stringResource(R.string.restore_replace), style = MaterialTheme.typography.titleSmall) }
                    FitTextButton(stringResource(R.string.action_cancel), onClick = viewModel::cancelStaged)
                }
                state.pendingEncrypted != null -> {
                    Text(stringResource(R.string.restore_password_needed), style = MaterialTheme.typography.bodyMedium)
                    PasswordField(state.restorePassword, viewModel::setRestorePassword, stringResource(R.string.backup_password), error = null)
                    FitPrimaryButton(stringResource(R.string.restore_open), onClick = viewModel::unlock, enabled = !state.checking && state.restorePassword.isNotEmpty(), modifier = Modifier.fillMaxWidth())
                }
                else -> FitSecondaryButton(
                    stringResource(R.string.restore_pick),
                    onClick = { openFile.launch(arrayOf("*/*")) },
                    enabled = !state.checking,
                    icon = FitIcons.Upload,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.checking || state.restoring) {
                Text(stringResource(if (state.restoring) R.string.restore_working else R.string.restore_checking), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.track)
            }
            state.restoreError?.let { FitNote(it.message(), warning = true) }
        }

        if (state.safety.isNotEmpty()) {
            SectionTitle(stringResource(R.string.safety_title))
            FitCard {
                Text(stringResource(R.string.safety_body), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                state.safety.forEach { item ->
                    val (date, time) = dateTime(item.createdAt)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.safety_row, date, time), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        FitTextButton(stringResource(R.string.safety_restore), onClick = { viewModel.stageSafety(item) })
                    }
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                FitTextButton(stringResource(R.string.restore_confirm_yes), onClick = {
                    confirming = false
                    viewModel.restore()
                })
            },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { confirming = false }) },
        )
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, error: String?) {
    val colors = FitTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorContainerColor = colors.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Preview(summary: BackupSummary) {
    val colors = FitTheme.colors
    val (date, time) = dateTime(summary.createdAt, summary.zone)
    Text(stringResource(R.string.restore_preview_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.restore_preview_date, date, time), style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(R.string.restore_preview_app, PersianNumbers.digits(summary.appVersionName)) +
            if (summary.encrypted) " · " + stringResource(R.string.restore_preview_encrypted) else "",
        style = MaterialTheme.typography.bodySmall,
        color = colors.subtle,
    )
    val counts = summary.counts
    val known = listOf("profile", "weight_entry", "body_measurement", "nutrition_target", "weight_goal")
    val lines = buildList {
        if ((counts["profile"] ?: 0) > 0) add(stringResource(R.string.count_profile))
        counts["weight_entry"]?.takeIf { it > 0 }?.let { add(stringResource(R.string.count_weight, Fmt.int(it))) }
        counts["body_measurement"]?.takeIf { it > 0 }?.let { add(stringResource(R.string.count_measurements, Fmt.int(it))) }
        counts["nutrition_target"]?.takeIf { it > 0 }?.let { add(stringResource(R.string.count_targets, Fmt.int(it))) }
        counts["weight_goal"]?.takeIf { it > 0 }?.let { add(stringResource(R.string.count_goals, Fmt.int(it))) }
        if (summary.photoCount > 0) add(stringResource(R.string.count_photos, Fmt.int(summary.photoCount)))
        val other = counts.filterKeys { it !in known }.values.sum()
        if (other > 0) add(stringResource(R.string.count_other, Fmt.int(other)))
    }
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s2)) {
        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
    }
}
