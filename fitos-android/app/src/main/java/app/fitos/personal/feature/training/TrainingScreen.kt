package app.fitos.personal.feature.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.EmptyState
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.MuscleMap
import app.fitos.core.designsystem.component.bestSide
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.EasyStart
import app.fitos.core.domain.training.Muscle
import app.fitos.core.domain.training.PlannedVolume
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramDay
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.ProgramSummary
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.ReturnChoice
import app.fitos.core.domain.training.ReturnPlan
import app.fitos.core.domain.training.ReturnPlanRepository
import app.fitos.core.domain.training.Schedule
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.SessionEstimate
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.core.domain.training.WorkoutSession
import app.fitos.personal.R
import app.fitos.personal.feature.home.easyText
import app.fitos.personal.ui.Fmt
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrainingState(
    val loaded: Boolean = false,
    val programs: List<ProgramSummary> = emptyList(),
    val active: Program? = null,
    val next: ProgramDay? = null,
    val volume: Map<Muscle, Double> = emptyMap(),
    /** The unfinished session, if any. */
    val open: WorkoutSession? = null,
    val sessionsDone: Int = 0,
    val doneToday: Boolean = false,
    /** The easier start chosen after a break, while it lasts. */
    val easy: EasyStart? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModel(
    private val programs: ProgramRepository,
    private val profiles: ProfileRepository,
    private val workouts: WorkoutRepository,
    returnPlan: ReturnPlanRepository,
    private val clock: Clock,
) : ViewModel() {
    private val lastDone = programs.active.flatMapLatest { p -> if (p == null) flowOf(null) else workouts.lastCompleted(p.id) }
    private val history = combine(workouts.history, returnPlan.latest) { h, latest -> h to latest }

    val state: StateFlow<TrainingState> = combine(programs.programs, programs.active, lastDone, workouts.active, history) { list, active, last, open, (history, latest) ->
        val today = LocalDate.now(clock)
        TrainingState(
            loaded = true,
            programs = list,
            active = active,
            next = active?.let { Schedule.nextAfter(it, last, today) },
            volume = active?.let { PlannedVolume.perCycle(it) }.orEmpty(),
            open = open,
            sessionsDone = history.size,
            doneToday = history.any { it.day == today },
            easy = if (open == null) ReturnPlan.next(latest, clock.instant(), history.map { it.startedAt }) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingState())

    /** Starts the day (or opens the session already running) and then [onReady]; lighter while an easy start lasts. */
    fun start(programId: String, dayId: String, kind: VariantKind, onReady: () -> Unit) {
        viewModelScope.launch {
            if (workouts.active.first() == null) {
                val day = state.value.active?.days?.firstOrNull { it.id == dayId }
                workouts.start(programId, dayId, kind, lighter = ReturnPlan.lightens(state.value.easy, kind, day?.hasMinimal == true))
            }
            onReady()
        }
    }

    fun createFromTemplate() {
        viewModelScope.launch {
            val equipment = profiles.profile.first()?.homeEquipment.orEmpty()
            programs.createFromTemplate(ProgramTemplates.upperFocus5, equipment)
        }
    }

    fun createEmpty(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch { onCreated(programs.createEmpty(name)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel,
    onOpenProgram: (String) -> Unit,
    onOpenDay: (programId: String, dayId: String) -> Unit,
    onOpenPrograms: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSession: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val newName = stringResource(R.string.training_new_program_name)
    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.training_title), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenLibrary) { Icon(FitIcons.Book, contentDescription = stringResource(R.string.training_library)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onSurface, actionIconContentColor = colors.onSurface),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = FitSpacing.gutter, end = FitSpacing.gutter, bottom = FitSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.gapSection),
        ) {
            val active = state.active
            if (state.programs.isEmpty() || active == null) {
                item {
                    FitCard {
                        EmptyState(FitEmoji.LIFTING, stringResource(R.string.training_empty_title), stringResource(R.string.training_empty_body)) {
                            Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                                FitPrimaryButton(stringResource(R.string.training_from_template), onClick = viewModel::createFromTemplate, modifier = Modifier.fillMaxWidth())
                                FitSecondaryButton(stringResource(R.string.training_empty_program), onClick = { viewModel.createEmpty(newName, onOpenProgram) }, modifier = Modifier.fillMaxWidth())
                                if (state.programs.isNotEmpty()) FitSecondaryButton(stringResource(R.string.training_all_programs), onClick = onOpenPrograms, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                return@LazyColumn
            }
            item { ProgramHeader(active, onEdit = { onOpenProgram(active.id) }, onAll = onOpenPrograms) }
            val open = state.open
            if (open != null) {
                item { ResumeCard(open, onOpenSession) }
            } else {
                if (state.doneToday) item { FitNote(stringResource(R.string.training_done_today)) }
                state.next?.let { next ->
                    item {
                        NextCard(
                            next,
                            easy = state.easy,
                            onOpen = { onOpenDay(active.id, next.id) },
                            onStart = { kind -> viewModel.start(active.id, next.id, kind, onOpenSession) },
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.training_days), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                    items(active.days, key = { it.id }) { day ->
                        DayTile(day, isNext = day.id == state.next?.id, onClick = { onOpenDay(active.id, day.id) })
                    }
                }
            }
            if (state.volume.isNotEmpty()) item { VolumeCard(state.volume) }
            item {
                FitSecondaryButton(
                    stringResource(R.string.training_history) + " · " + stringResource(R.string.training_sessions_done, Fmt.int(state.sessionsDone)),
                    onClick = onOpenHistory,
                    icon = FitIcons.History,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProgramHeader(program: Program, onEdit: () -> Unit, onAll: () -> Unit) {
    val colors = FitTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
        Text(program.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.program_days_count, Fmt.int(program.trainingDays.size)) + " · " +
                stringResource(if (program.scheduleMode == ScheduleMode.SEQUENCE) R.string.schedule_sequence else R.string.schedule_weekdays),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FitSecondaryButton(stringResource(R.string.training_edit_program), onClick = onEdit, icon = FitIcons.Edit, modifier = Modifier.weight(1f))
            FitSecondaryButton(stringResource(R.string.training_all_programs), onClick = onAll, icon = FitIcons.List, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NextCard(day: ProgramDay, easy: EasyStart?, onOpen: () -> Unit, onStart: (VariantKind) -> Unit) {
    val colors = FitTheme.colors
    val (primary, secondary) = PlannedVolume.muscles(day.full)
    FitCard(hero = true) {
        Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                Text(stringResource(R.string.training_next), style = MaterialTheme.typography.labelLarge, color = colors.subtle)
                Text(day.name, style = MaterialTheme.typography.titleLarge)
                day.preferredWeekday?.let { Text(JalaliCalendar.weekdayName(it), style = MaterialTheme.typography.bodySmall, color = colors.subtle) }
                Text(muscleNames(primary), style = MaterialTheme.typography.bodyMedium, color = colors.training.ink)
            }
            MuscleMap(bestSide(primary, secondary), primary, secondary, width = 44.dp)
        }
        easy?.let { FitNote(easyText(it)) }
        val minimalFirst = easy?.choice == ReturnChoice.MINIMAL && day.hasMinimal
        if (minimalFirst) {
            val minimal = day.variant(VariantKind.MINIMAL)!!.exercises
            FitPrimaryButton(
                stringResource(VariantKind.MINIMAL.label) + " · " + stringResource(R.string.minutes_short, Fmt.int(SessionEstimate.minutes(minimal))),
                onClick = { onStart(VariantKind.MINIMAL) },
                icon = FitIcons.Play,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (day.full.isNotEmpty()) {
            Text(
                stringResource(VariantKind.FULL.label) + " · " +
                    stringResource(R.string.training_exercises_minutes, Fmt.int(day.full.size), Fmt.int(SessionEstimate.minutes(day.full))),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            val lighter = ReturnPlan.lightens(easy, VariantKind.FULL, day.hasMinimal)
            val label = stringResource(if (lighter) R.string.training_start_lighter else R.string.training_start)
            if (minimalFirst) {
                FitSecondaryButton(label, onClick = { onStart(VariantKind.FULL) }, icon = FitIcons.Play, modifier = Modifier.fillMaxWidth())
            } else {
                FitPrimaryButton(label, onClick = { onStart(VariantKind.FULL) }, icon = FitIcons.Play, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text(stringResource(R.string.training_no_exercises), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        val shorter = listOf(VariantKind.HOME, VariantKind.MINIMAL).filter { day.variant(it)?.exercises?.isNotEmpty() == true && !(it == VariantKind.MINIMAL && minimalFirst) }
        if (shorter.isNotEmpty()) {
            Text(stringResource(R.string.training_start_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                shorter.forEach { kind ->
                    val exercises = day.variant(kind)!!.exercises
                    FitSecondaryButton(
                        stringResource(kind.label) + " · " + stringResource(R.string.minutes_short, Fmt.int(SessionEstimate.minutes(exercises))),
                        onClick = { onStart(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A session left open (the app was closed or the phone restarted): carry on where it stopped. */
@Composable
private fun ResumeCard(session: WorkoutSession, onOpen: () -> Unit) {
    val colors = FitTheme.colors
    FitCard(hero = true) {
        Text(stringResource(R.string.training_resume_title), style = MaterialTheme.typography.labelLarge, color = colors.subtle)
        Text(
            stringResource(R.string.training_resume_body, session.dayName ?: stringResource(R.string.session_title), Fmt.int(session.workingSets)),
            style = MaterialTheme.typography.titleLarge,
        )
        FitPrimaryButton(stringResource(R.string.training_resume), onClick = onOpen, icon = FitIcons.Play, modifier = Modifier.fillMaxWidth())
    }
}

/** Design-system day tile: number, muscles, name, meta; the next day is outlined, rest days dashed. */
@Composable
private fun DayTile(day: ProgramDay, isNext: Boolean, onClick: () -> Unit) {
    val colors = FitTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val (primary, secondary) = PlannedVolume.muscles(day.full)
    val border = when {
        isNext -> BorderStroke(2.dp, colors.primary)
        day.isRest -> BorderStroke(1.dp, colors.outlineVariant)
        else -> null
    }
    Column(
        Modifier
            .width(76.dp)
            .heightIn(min = 140.dp)
            .clip(shape)
            .background(if (day.isRest) colors.background else if (isNext) colors.surface else colors.surfaceContainer)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = FitSpacing.s8, horizontal = FitSpacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s4),
    ) {
        Text(Fmt.int(day.position + 1), style = numeralStyle(20), color = colors.onSurface)
        if (day.isRest) {
            Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) { Icon(FitIcons.Moon, contentDescription = null, tint = colors.onSurfaceVariant) }
        } else {
            MuscleMap(bestSide(primary, secondary), primary, secondary, width = 26.dp)
        }
        Text(
            if (day.isRest) stringResource(R.string.training_rest) else day.name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        day.preferredWeekday?.let { Text(JalaliCalendar.weekdayName(it), style = MaterialTheme.typography.bodySmall, color = colors.subtle) }
    }
}

@Composable
private fun VolumeCard(volume: Map<Muscle, Double>) {
    val colors = FitTheme.colors
    val max = volume.values.maxOrNull() ?: 1.0
    FitCard {
        Text(stringResource(R.string.training_volume), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        volume.forEach { (muscle, sets) ->
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "${muscle.nameFa}: ${Fmt.kg(sets)}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8),
            ) {
                Text(muscle.nameFa, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(88.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.track)) {
                    Box(Modifier.fillMaxWidth((sets / max).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.training.fill))
                }
                Text(Fmt.kg(sets), style = numeralStyle(14), color = colors.onSurface, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
            }
        }
        Text(stringResource(R.string.training_volume_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    }
}
