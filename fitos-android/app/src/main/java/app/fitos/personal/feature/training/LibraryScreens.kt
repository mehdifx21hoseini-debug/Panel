package app.fitos.personal.feature.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.BodySide
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.component.MuscleMap
import app.fitos.core.designsystem.component.bestSide
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.training.Equipment
import app.fitos.core.domain.training.Exercise
import app.fitos.core.domain.training.ExerciseFilter
import app.fitos.core.domain.training.ExerciseInput
import app.fitos.core.domain.training.ExerciseRepository
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Muscle
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.TrainingRules
import app.fitos.core.domain.training.TrainingRules.ExerciseProblem
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.core.domain.training.ExercisePerformance
import app.fitos.core.domain.training.PersonalRecord
import app.fitos.core.domain.training.RecordType
import app.fitos.core.domain.training.Strength
import app.fitos.core.designsystem.component.ChartPoint
import app.fitos.core.designsystem.component.TrendChart
import app.fitos.core.domain.time.DateFormats
import app.fitos.personal.feature.session.recordText
import app.fitos.personal.feature.session.setText
import androidx.compose.material3.HorizontalDivider
import java.time.LocalDate
import kotlin.math.roundToInt
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Browse the library, pick an exercise for a program day or a replacement for a planned one,
 * or do the same inside a running session.
 */
enum class LibraryMode { BROWSE, PICK, REPLACE, SESSION_ADD, SESSION_REPLACE }

class LibraryViewModel(
    private val exercises: ExerciseRepository,
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository,
    val mode: LibraryMode,
    private val dayId: String? = null,
    private val kind: VariantKind = VariantKind.FULL,
    private val plannedId: String? = null,
    private val sessionId: String? = null,
    private val sessionExerciseId: String? = null,
) : ViewModel() {
    private val _filter = MutableStateFlow(ExerciseFilter())
    val filter: StateFlow<ExerciseFilter> = _filter.asStateFlow()
    val results: StateFlow<List<Exercise>?> = combine(exercises.all, _filter) { all, f -> f.apply(all) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var choosing = false

    fun setFilter(filter: ExerciseFilter) {
        _filter.value = filter
    }

    fun toggleFavorite(e: Exercise) = viewModelScope.launch { exercises.setFavorite(e.id, !e.isFavorite) }

    /** Adds or swaps in the exercise, then [onDone]; a second tap while saving is ignored. */
    fun choose(id: String, onDone: () -> Unit) {
        if (choosing) return
        choosing = true
        viewModelScope.launch {
            when (mode) {
                LibraryMode.PICK -> programs.addExercise(requireNotNull(dayId), kind, id)
                LibraryMode.REPLACE -> programs.replaceExercise(requireNotNull(plannedId), id)
                LibraryMode.SESSION_ADD -> workouts.addExercise(requireNotNull(sessionId), id)
                LibraryMode.SESSION_REPLACE -> workouts.replaceExercise(requireNotNull(sessionExerciseId), id)
                LibraryMode.BROWSE -> Unit
            }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onNewExercise: () -> Unit,
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val title = when (viewModel.mode) {
        LibraryMode.BROWSE -> R.string.library_title
        LibraryMode.PICK -> R.string.library_pick_title
        LibraryMode.REPLACE, LibraryMode.SESSION_REPLACE -> R.string.library_replace_title
        LibraryMode.SESSION_ADD -> R.string.library_session_add_title
    }
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(FitIcons.Back, contentDescription = stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onSurface, navigationIconContentColor = colors.onSurface),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = FitSpacing.gutter, end = FitSpacing.gutter, bottom = FitSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s8),
        ) {
            item {
                FitTextField(
                    value = filter.query,
                    onValueChange = { viewModel.setFilter(filter.copy(query = it.take(TrainingRules.NAME_MAX))) },
                    label = stringResource(R.string.library_search),
                    imeAction = ImeAction.Search,
                )
            }
            item { LibraryFilters(filter, viewModel::setFilter) }
            val list = results ?: return@LazyColumn
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.library_count, Fmt.int(list.size)), style = MaterialTheme.typography.bodySmall, color = colors.subtle, modifier = Modifier.weight(1f))
                    FitTextButton(stringResource(R.string.library_new), onClick = onNewExercise)
                }
            }
            if (list.isEmpty()) {
                item { FitNote(stringResource(R.string.library_empty)) }
                if (!filter.isEmpty) item { FitSecondaryButton(stringResource(R.string.library_clear), onClick = { viewModel.setFilter(ExerciseFilter()) }, modifier = Modifier.fillMaxWidth()) }
            }
            items(list, key = { it.id }) { e ->
                ExerciseRow(
                    e,
                    onClick = { if (viewModel.mode == LibraryMode.BROWSE) onOpenExercise(e.id) else viewModel.choose(e.id, onBack) },
                    onFavorite = { viewModel.toggleFavorite(e) },
                    onInfo = if (viewModel.mode == LibraryMode.BROWSE) null else ({ onOpenExercise(e.id) }),
                )
            }
        }
    }
}

/** Favourites / own exercises, then one row of muscles and one of equipment (single choice each). */
@Composable
private fun LibraryFilters(filter: ExerciseFilter, onChange: (ExerciseFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FilterPill(stringResource(R.string.library_favorites), filter.favoritesOnly) { onChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) }
            FilterPill(stringResource(R.string.library_mine), filter.mineOnly) { onChange(filter.copy(mineOnly = !filter.mineOnly)) }
            Equipment.entries.forEach { eq ->
                FilterPill(eq.nameFa, filter.equipment == eq) { onChange(filter.copy(equipment = if (filter.equipment == eq) null else eq)) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FilterPill(stringResource(R.string.library_all_muscles), filter.muscle == null) { onChange(filter.copy(muscle = null)) }
            Muscle.entries.forEach { m ->
                FilterPill(m.nameFa, filter.muscle == m) { onChange(filter.copy(muscle = if (filter.muscle == m) null else m)) }
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = FitTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (selected) {
            { Icon(FitIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface,
            labelColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
            selectedLeadingIconColor = colors.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = colors.outline, selectedBorderColor = colors.primary),
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

@Composable
private fun ExerciseRow(e: Exercise, onClick: () -> Unit, onFavorite: () -> Unit, onInfo: (() -> Unit)?) {
    val colors = FitTheme.colors
    FitCard(contentPadding = PaddingValues(start = FitSpacing.s12, end = FitSpacing.s4, top = FitSpacing.s4, bottom = FitSpacing.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).heightIn(min = 64.dp).padding(vertical = FitSpacing.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
            ) {
                MuscleMap(bestSide(e.primary, e.secondary), e.primary, e.secondary, width = 26.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s2)) {
                    Text(e.nameFa, style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(muscleNames(e.primary), e.equipment.nameFa, if (e.isCustom) stringResource(R.string.exercise_custom) else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (onInfo != null) {
                IconButton(onClick = onInfo) { Icon(FitIcons.Info, contentDescription = stringResource(R.string.library_details, e.nameFa)) }
            }
            FavoriteButton(e.isFavorite, e.nameFa, onFavorite)
        }
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, name: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (favorite) FitIcons.StarFilled else FitIcons.Star,
            contentDescription = stringResource(if (favorite) R.string.favorite_remove_named else R.string.favorite_add_named, name),
            tint = if (favorite) FitTheme.colors.primary else FitTheme.colors.onSurfaceVariant,
        )
    }
}

// ---- one exercise ----

class ExerciseDetailViewModel(private val exercises: ExerciseRepository, workouts: WorkoutRepository, val exerciseId: String) : ViewModel() {
    /** Null while loading; a deleted own exercise stays readable (programs may still show it). */
    val exercise: StateFlow<Exercise?> = exercises.exercise(exerciseId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history: StateFlow<List<ExercisePerformance>> = workouts.exerciseHistory(exerciseId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val records: StateFlow<List<PersonalRecord>> = workouts.records(exerciseId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite() = viewModelScope.launch { exercise.value?.let { exercises.setFavorite(it.id, !it.isFavorite) } }
    suspend fun usageCount(): Int = exercises.usageCount(exerciseId)
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        exercises.deleteCustom(exerciseId)
        onDone()
    }
}

@Composable
fun ExerciseDetailScreen(viewModel: ExerciseDetailViewModel, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val scope = rememberCoroutineScope()
    var deleteUsage by remember { mutableStateOf<Int?>(null) }

    EditScaffold(title = exercise?.nameFa.orEmpty(), onBack = onBack) {
        val e = exercise ?: return@EditScaffold
        FitCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s24, Alignment.CenterHorizontally)) {
                MuscleMap(BodySide.FRONT, e.primary, e.secondary, width = 80.dp)
                MuscleMap(BodySide.BACK, e.primary, e.secondary, width = 80.dp)
            }
            InfoLine(stringResource(R.string.exercise_primary), muscleNames(e.primary))
            if (e.secondary.isNotEmpty()) InfoLine(stringResource(R.string.exercise_secondary), muscleNames(e.secondary))
            InfoLine(stringResource(R.string.exercise_equipment), e.equipment.nameFa)
            InfoLine(stringResource(R.string.exercise_log_type), stringResource(e.logType.label))
            e.nameEn?.let { InfoLine(stringResource(R.string.exercise_name_en), it) }
            if (e.isCustom) Text(stringResource(R.string.exercise_custom), style = MaterialTheme.typography.labelMedium, color = colors.training.ink)
        }
        e.instructions?.let { text ->
            SectionTitle(stringResource(if (e.isCustom) R.string.exercise_notes else R.string.exercise_how))
            FitCard { Text(text, style = MaterialTheme.typography.bodyMedium) }
        }
        ExerciseProgress(e, history, records)
        FitSecondaryButton(
            stringResource(if (e.isFavorite) R.string.favorite_remove else R.string.favorite_add),
            onClick = viewModel::toggleFavorite,
            icon = if (e.isFavorite) FitIcons.StarFilled else FitIcons.Star,
            modifier = Modifier.fillMaxWidth(),
        )
        if (e.isCustom) {
            FitSecondaryButton(stringResource(R.string.exercise_edit), onClick = { onEdit(e.id) }, icon = FitIcons.Edit, modifier = Modifier.fillMaxWidth())
            FitTextButton(stringResource(R.string.exercise_delete), onClick = { scope.launch { deleteUsage = viewModel.usageCount() } })
        }
    }

    deleteUsage?.let { used ->
        AlertDialog(
            onDismissRequest = { deleteUsage = null },
            title = { Text(stringResource(R.string.exercise_delete_title)) },
            text = { Text(if (used > 0) stringResource(R.string.exercise_delete_used, Fmt.int(used)) else stringResource(R.string.exercise_delete_unused)) },
            confirmButton = {
                FitTextButton(stringResource(R.string.action_delete), onClick = {
                    deleteUsage = null
                    viewModel.delete(onBack)
                })
            },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { deleteUsage = null }) },
        )
    }
}

/** Records, the e1RM line over sessions and the latest sessions of one exercise. */
@Composable
private fun ExerciseProgress(e: Exercise, history: List<ExercisePerformance>, records: List<PersonalRecord>) {
    val colors = FitTheme.colors
    SectionTitle(stringResource(R.string.exercise_history))
    if (history.isEmpty()) {
        FitNote(stringResource(R.string.exercise_no_history))
        return
    }
    Text(stringResource(R.string.training_sessions_done, Fmt.int(history.size)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    val points = history.reversed().mapNotNull { p ->
        p.working.mapNotNull { Strength.e1rm(it.weightKg, it.reps) }.maxOrNull()?.let { ChartPoint(p.day.toEpochDay(), it, it) }
    }
    if (e.logType == LogType.WEIGHT_REPS && points.size >= 2) {
        FitCard {
            Text(stringResource(R.string.exercise_chart), style = MaterialTheme.typography.titleSmall)
            TrendChart(
                points = points,
                description = stringResource(R.string.exercise_chart) + ": " + points.joinToString("، ") { Fmt.kg(it.value) },
                valueLabel = { Fmt.int(it.roundToInt()) },
                dateLabel = { DateFormats.dayMonth(LocalDate.ofEpochDay(it)) },
                tooltip = { DateFormats.dayMonth(LocalDate.ofEpochDay(it.x)) + " · " + Fmt.kg(it.value) },
                height = 160.dp,
            )
        }
    }
    val shown = records.filter { it.type != RecordType.MAX_WEIGHT_FOR_REPS } +
        records.filter { it.type == RecordType.MAX_WEIGHT_FOR_REPS }.sortedByDescending { it.value }.take(3)
    if (shown.isNotEmpty()) {
        FitCard {
            Text(stringResource(R.string.exercise_records), style = MaterialTheme.typography.titleSmall)
            shown.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8), verticalAlignment = Alignment.CenterVertically) {
                    Icon(FitIcons.Star, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                    Text(recordText(r), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(DateFormats.dayMonth(r.day), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
            }
        }
    }
    FitCard {
        history.take(6).forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = colors.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s2)) {
                Text(DateFormats.longDate(p.day), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                Text(p.working.map { setText(e.logType, it) }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FitTheme.colors.subtle)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ---- own exercise form ----

class ExerciseFormViewModel(private val exercises: ExerciseRepository, val exerciseId: String?) : ViewModel() {
    /** What the form starts from: the saved exercise, or sensible defaults for a new one. */
    val initial: StateFlow<ExerciseInput?> = (
        if (exerciseId == null) {
            MutableStateFlow(ExerciseInput("", null, Equipment.DUMBBELL, LogType.WEIGHT_REPS, emptySet(), emptySet(), 2.5, null))
        } else {
            exercises.exercise(exerciseId).map { e ->
                e?.let { ExerciseInput(it.nameFa, it.nameEn, it.equipment, it.logType, it.primary, it.secondary, it.weightIncrementKg, it.instructions) }
            }
        }
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var saving = false

    fun save(input: ExerciseInput, onSaved: (String) -> Unit) {
        if (saving || TrainingRules.check(input).isNotEmpty()) return
        saving = true
        viewModelScope.launch {
            val id = if (exerciseId == null) exercises.addCustom(input).id else exerciseId.also { exercises.updateCustom(it, input) }
            onSaved(id)
        }
    }
}

@Composable
fun ExerciseFormScreen(viewModel: ExerciseFormViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val initial by viewModel.initial.collectAsStateWithLifecycle()
    val start = initial
    val title = stringResource(if (viewModel.exerciseId == null) R.string.exercise_form_new else R.string.exercise_form_edit)
    if (start == null) {
        EditScaffold(title = title, onBack = onBack) {}
        return
    }
    var name by remember { mutableStateOf(start.nameFa) }
    var nameEn by remember { mutableStateOf(start.nameEn.orEmpty()) }
    var equipment by remember { mutableStateOf(start.equipment) }
    var logType by remember { mutableStateOf(start.logType) }
    var primary by remember { mutableStateOf(start.primary) }
    var secondary by remember { mutableStateOf(start.secondary) }
    var increment by remember { mutableStateOf(start.weightIncrementKg?.let(PersianNumbers::format).orEmpty()) }
    var notes by remember { mutableStateOf(start.instructions.orEmpty()) }
    var tried by remember { mutableStateOf(false) }

    fun input() = ExerciseInput(
        nameFa = name,
        nameEn = nameEn,
        equipment = equipment,
        logType = logType,
        primary = primary,
        secondary = secondary - primary,
        weightIncrementKg = if (logType == LogType.WEIGHT_REPS) PersianNumbers.parse(increment) else null,
        instructions = notes,
    )
    // Errors appear after the first save attempt and clear as soon as the field is fixed.
    val problems = if (tried) TrainingRules.check(input()) else emptySet()

    EditScaffold(
        title = title,
        onBack = onBack,
        bottomBar = {
            FitPrimaryButton(stringResource(R.string.action_save), onClick = {
                tried = true
                viewModel.save(input(), onSaved)
            }, modifier = Modifier.fillMaxWidth())
        },
    ) {
        FitTextField(
            value = name,
            onValueChange = { name = it.take(TrainingRules.NAME_MAX) },
            label = stringResource(R.string.exercise_name),
            error = if (ExerciseProblem.NAME in problems) stringResource(R.string.error_exercise_name) else null,
        )
        FitTextField(value = nameEn, onValueChange = { nameEn = it.take(TrainingRules.NAME_MAX) }, label = stringResource(R.string.exercise_name_en) + " (" + stringResource(R.string.optional) + ")")

        FieldLabel(stringResource(R.string.exercise_equipment))
        FitChips(Equipment.entries, setOf(equipment), { equipment = it }, label = { it.nameFa }, multi = false)

        FieldLabel(stringResource(R.string.exercise_log_type))
        FitChips(LogType.entries, setOf(logType), { logType = it }, label = { stringResource(it.label) }, multi = false)
        if (logType == LogType.WEIGHT_REPS) {
            FitNumberField(
                increment,
                { increment = it },
                stringResource(R.string.field_increment),
                suffix = stringResource(R.string.unit_kg),
                maxIntegerDigits = 2,
                maxFractionDigits = 2,
                error = if (ExerciseProblem.INCREMENT in problems) stringResource(R.string.error_increment) else null,
            )
        }

        FieldLabel(stringResource(R.string.exercise_primary_pick))
        FitChips(Muscle.entries, primary, { m -> primary = if (m in primary) primary - m else primary + m }, label = { it.nameFa })
        if (ExerciseProblem.PRIMARY in problems) {
            Text(stringResource(R.string.error_exercise_primary), style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.error)
        }
        FieldLabel(stringResource(R.string.exercise_secondary_pick))
        FitChips(Muscle.entries.filter { it !in primary }, secondary, { m -> secondary = if (m in secondary) secondary - m else secondary + m }, label = { it.nameFa })

        if (primary.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s24, Alignment.CenterHorizontally)) {
                MuscleMap(BodySide.FRONT, primary, secondary - primary, width = 56.dp)
                MuscleMap(BodySide.BACK, primary, secondary - primary, width = 56.dp)
            }
        }
        FitTextField(
            value = notes,
            onValueChange = { notes = it.take(1000) },
            label = stringResource(R.string.exercise_notes) + " (" + stringResource(R.string.optional) + ")",
            imeAction = ImeAction.Default,
            minLines = 3,
        )
    }
}
