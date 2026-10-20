package app.fitos.personal.feature.targets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.nutrition.TargetCalculator
import app.fitos.core.domain.nutrition.TargetInput
import app.fitos.core.domain.profile.InputError
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.profile.WeightGoalRepository
import app.fitos.core.domain.time.DateFormats
import app.fitos.personal.R
import app.fitos.personal.ui.EditScaffold
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TargetsState(
    val form: TargetsForm? = null,
    val since: LocalDate? = null,
    val errors: Map<TargetField, InputError> = emptyMap(),
    val missingWeight: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
)

/** Edits today's targets. Saving starts a new dated target from today; the past keeps its own. */
class TargetsViewModel(
    private val targets: NutritionTargetRepository,
    private val profiles: ProfileRepository,
    private val weights: WeightRepository,
    private val goals: WeightGoalRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val _state = MutableStateFlow(TargetsState())
    val state: StateFlow<TargetsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = targets.effectiveOn(today).first()
            if (current != null) {
                _state.update { it.copy(form = TargetsForm.from(current), since = current.effectiveFrom) }
            } else {
                recalculate()
            }
        }
    }

    fun update(change: TargetsForm.() -> TargetsForm) = _state.update { s ->
        val form = s.form?.change() ?: return@update s
        s.copy(form = form, errors = form.errors().filterKeys { it in s.errors })
    }

    /** Replaces the fields with a fresh suggestion from the current profile, latest weight and goal. */
    fun recalculate() {
        viewModelScope.launch {
            val profile = profiles.profile.first() ?: return@launch
            val weight = weights.latest.first()?.weightKg
            if (weight == null) {
                _state.update { it.copy(missingWeight = true) }
                return@launch
            }
            val goal = goals.activeGoal.first()
            val suggestion = TargetCalculator.suggest(
                TargetInput(
                    sex = profile.sex,
                    ageYears = profile.ageOn(today),
                    weightKg = weight,
                    heightCm = profile.heightCm,
                    activity = profile.activityLevel,
                    goal = profile.primaryGoal,
                    pace = profile.energyPace,
                    targetWeightKg = goal?.targetWeightKg.takeIf { profile.primaryGoal != PrimaryGoal.MAINTAIN },
                ),
                effectiveFrom = today,
            )
            _state.update { it.copy(form = TargetsForm.from(suggestion), errors = emptyMap(), missingWeight = false) }
        }
    }

    fun save() {
        val form = _state.value.form ?: return
        val errors = form.errors()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching { targets.save(form.toTarget(today)) }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { _state.update { it.copy(saving = false, saveFailed = true) } }
        }
    }
}

@Composable
fun TargetsScreen(viewModel: TargetsViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    EditScaffold(
        title = stringResource(R.string.targets_title),
        onBack = onDone,
        bottomBar = {
            if (state.saveFailed) FitNote(stringResource(R.string.error_save_failed), warning = true)
            FitPrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = !state.saving && state.form != null, modifier = Modifier.fillMaxWidth())
        },
    ) {
        state.since?.let {
            Text(stringResource(R.string.targets_since, DateFormats.longDate(it)), style = MaterialTheme.typography.bodyMedium, color = FitTheme.colors.onSurfaceVariant)
        }
        FitNote(stringResource(R.string.targets_saved_note))
        if (state.missingWeight) FitNote(stringResource(R.string.targets_missing_weight), warning = true)
        val form = state.form ?: return@EditScaffold
        TargetFields(form, state.errors, onChange = viewModel::update)
        FitSecondaryButton(stringResource(R.string.targets_recalculate), onClick = viewModel::recalculate, icon = FitIcons.Refresh, modifier = Modifier.fillMaxWidth())
        form.base.assumptions?.let { AssumptionsCard(it) }
    }
}
