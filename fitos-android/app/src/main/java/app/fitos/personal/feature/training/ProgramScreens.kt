package app.fitos.personal.feature.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitSegmented
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramChange
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.ProgramSummary
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.TrainingRules
import app.fitos.personal.R
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---- all programs ----

class ProgramsViewModel(private val programs: ProgramRepository, private val profiles: ProfileRepository) : ViewModel() {
    val list: StateFlow<List<ProgramSummary>?> = programs.programs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun activate(id: String) = viewModelScope.launch { programs.setActive(id) }
    fun duplicate(id: String, name: String) = viewModelScope.launch { programs.duplicate(id, name) }
    fun archive(id: String, archived: Boolean) = viewModelScope.launch { programs.archive(id, archived) }
    fun createFromTemplate() = viewModelScope.launch {
        programs.createFromTemplate(ProgramTemplates.upperFocus5, profiles.profile.first()?.homeEquipment.orEmpty())
    }
    fun createEmpty(name: String, onCreated: (String) -> Unit) = viewModelScope.launch { onCreated(programs.createEmpty(name)) }
}

@Composable
fun ProgramsScreen(viewModel: ProgramsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val copyFormat = stringResource(R.string.program_copy_name)
    val newName = stringResource(R.string.training_new_program_name)
    EditScaffold(title = stringResource(R.string.programs_title), onBack = onBack) {
        val programs = list ?: return@EditScaffold
        programs.forEach { p ->
            FitCard {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(p.id) }.heightIn(min = FitSpacing.target),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        val tags = listOfNotNull(
                            stringResource(R.string.program_days_count, Fmt.int(p.trainingDays)),
                            if (p.isActive) stringResource(R.string.program_active) else null,
                            if (p.archived) stringResource(R.string.program_archived) else null,
                        )
                        Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (p.isActive) colors.primary else colors.subtle)
                    }
                    Icon(FitIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                    if (!p.isActive) FitTextButton(stringResource(R.string.program_activate), onClick = { viewModel.activate(p.id) })
                    FitTextButton(stringResource(R.string.program_duplicate), onClick = { viewModel.duplicate(p.id, copyFormat.format(p.name)) })
                    FitTextButton(stringResource(if (p.archived) R.string.program_unarchive else R.string.program_archive), onClick = { viewModel.archive(p.id, !p.archived) })
                }
            }
        }
        FitPrimaryButton(stringResource(R.string.training_from_template), onClick = viewModel::createFromTemplate, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
        FitSecondaryButton(stringResource(R.string.training_empty_program), onClick = { viewModel.createEmpty(newName, onOpen) }, modifier = Modifier.fillMaxWidth())
    }
}

// ---- one program ----

class ProgramEditorViewModel(private val programs: ProgramRepository, val programId: String) : ViewModel() {
    val program: StateFlow<Program?> = programs.program(programId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) = viewModelScope.launch { if (name.isNotBlank()) programs.rename(programId, name.take(TrainingRules.NAME_MAX)) }
    fun setMode(mode: ScheduleMode) = viewModelScope.launch { programs.setScheduleMode(programId, mode) }
    fun addDay(name: String, rest: Boolean, onAdded: (String) -> Unit = {}) = viewModelScope.launch { onAdded(programs.addDay(programId, name, rest)) }
    fun moveDay(id: String, by: Int) = viewModelScope.launch { programs.moveDay(id, by) }
    fun deleteDay(id: String) = viewModelScope.launch { programs.deleteDay(id) }
    fun activate() = viewModelScope.launch { programs.setActive(programId) }
}

@Composable
fun ProgramEditorScreen(viewModel: ProgramEditorViewModel, onBack: () -> Unit, onOpenDay: (String) -> Unit, onOpenChanges: () -> Unit) {
    val program by viewModel.program.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val dayDefault = stringResource(R.string.program_day_default)

    EditScaffold(title = stringResource(R.string.program_editor_title), onBack = onBack) {
        val p = program ?: return@EditScaffold
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { renaming = true }) { Icon(FitIcons.Edit, contentDescription = stringResource(R.string.program_name)) }
            }
            if (!p.isActive) FitPrimaryButton(stringResource(R.string.program_activate), onClick = viewModel::activate, modifier = Modifier.fillMaxWidth())
        }

        SectionTitle(stringResource(R.string.program_schedule))
        FitSegmented(
            options = ScheduleMode.entries,
            selected = p.scheduleMode,
            onSelect = viewModel::setMode,
            label = { stringResource(if (it == ScheduleMode.SEQUENCE) R.string.schedule_sequence else R.string.schedule_weekdays) },
        )
        FitNote(stringResource(if (p.scheduleMode == ScheduleMode.SEQUENCE) R.string.schedule_hint_sequence else R.string.schedule_hint_weekdays))

        SectionTitle(stringResource(R.string.training_days))
        FitCard {
            p.days.forEachIndexed { index, day ->
                if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onOpenDay(day.id) }.heightIn(min = FitSpacing.target).padding(vertical = FitSpacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Fmt.int(index + 1), style = MaterialTheme.typography.titleMedium, color = colors.subtle, modifier = Modifier.padding(end = FitSpacing.s12))
                        Column(Modifier.weight(1f)) {
                            Text(if (day.isRest) stringResource(R.string.training_rest) else day.name, style = MaterialTheme.typography.bodyLarge)
                            val meta = listOfNotNull(
                                day.preferredWeekday?.let(JalaliCalendar::weekdayName),
                                if (!day.isRest) stringResource(R.string.training_exercises_minutes, Fmt.int(day.full.size), Fmt.int(app.fitos.core.domain.training.SessionEstimate.minutes(day.full))) else null,
                            )
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                        }
                    }
                    IconButton(onClick = { viewModel.moveDay(day.id, -1) }, enabled = index > 0) { Icon(FitIcons.ChevronUp, contentDescription = stringResource(R.string.move_up)) }
                    IconButton(onClick = { viewModel.moveDay(day.id, 1) }, enabled = index < p.days.lastIndex) { Icon(FitIcons.ChevronDown, contentDescription = stringResource(R.string.move_down)) }
                    IconButton(onClick = { deleting = day.id }) { Icon(FitIcons.Trash, contentDescription = stringResource(R.string.action_delete)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                FitSecondaryButton(
                    stringResource(R.string.program_add_day),
                    onClick = { viewModel.addDay(dayDefault.format(PersianNumbers.format(p.days.size + 1)), rest = false, onAdded = onOpenDay) },
                    enabled = p.days.size < TrainingRules.MAX_DAYS,
                    icon = FitIcons.Plus,
                    modifier = Modifier.weight(1f),
                )
                FitSecondaryButton(
                    stringResource(R.string.program_add_rest),
                    onClick = { viewModel.addDay("", rest = true) },
                    enabled = p.days.size < TrainingRules.MAX_DAYS,
                    icon = FitIcons.Moon,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        FitSecondaryButton(stringResource(R.string.program_changes), onClick = onOpenChanges, icon = FitIcons.History, modifier = Modifier.fillMaxWidth())
    }

    if (renaming) {
        var name by remember { mutableStateOf(program?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.program_name)) },
            text = { FitTextField(value = name, onValueChange = { name = it.take(TrainingRules.NAME_MAX) }, label = stringResource(R.string.program_name)) },
            confirmButton = { FitTextButton(stringResource(R.string.action_save), onClick = { viewModel.rename(name); renaming = false }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { renaming = false }) },
        )
    }
    deleting?.let { id ->
        val day = program?.days?.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.day_delete_title)) },
            text = { Text(stringResource(R.string.day_delete_body, day?.name?.ifBlank { null } ?: stringResource(R.string.training_rest))) },
            confirmButton = { FitTextButton(stringResource(R.string.action_delete), onClick = { viewModel.deleteDay(id); deleting = null }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { deleting = null }) },
        )
    }
}

// ---- change log ----

class ChangeLogViewModel(programs: ProgramRepository, programId: String) : ViewModel() {
    val changes: StateFlow<List<ProgramChange>?> = programs.changes(programId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ChangeLogScreen(viewModel: ChangeLogViewModel, onBack: () -> Unit) {
    val changes by viewModel.changes.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    EditScaffold(title = stringResource(R.string.changes_title), onBack = onBack) {
        val list = changes ?: return@EditScaffold
        if (list.isEmpty()) Text(stringResource(R.string.changes_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        list.forEach { c ->
            val local = c.at.atZone(ZoneId.systemDefault())
            Column(Modifier.fillMaxWidth().padding(vertical = FitSpacing.s4)) {
                Text(c.summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    DateFormats.longDate(local.toLocalDate()) + " · " + PersianNumbers.digits("%02d:%02d".format(local.hour, local.minute)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.subtle,
                )
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}
