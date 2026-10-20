package app.fitos.personal.feature.profile

import androidx.compose.foundation.layout.fillMaxWidth
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
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.profile.InputError
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.core.domain.profile.WeightGoalRepository
import app.fitos.personal.R
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.SectionTitle
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditState(
    val form: ProfileForm? = null,
    val errors: Map<ProfileField, InputError> = emptyMap(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
)

class ProfileEditViewModel(
    private val profiles: ProfileRepository,
    private val goals: WeightGoalRepository,
    private val weights: WeightRepository,
    clock: Clock,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    private var activeGoal: WeightGoal? = null
    private var latestWeightKg: Double? = null

    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profiles.profile.first() ?: return@launch
            activeGoal = goals.activeGoal.first()
            latestWeightKg = weights.latest.first()?.weightKg
            _state.update { it.copy(form = ProfileForm.from(profile, activeGoal)) }
        }
    }

    private fun validate(form: ProfileForm) =
        form.aboutErrors(today, requireWeight = false) + form.goalErrors(today, latestWeightKg)

    fun update(change: ProfileForm.() -> ProfileForm) = _state.update { s ->
        val form = s.form?.change() ?: return@update s
        s.copy(form = form, errors = validate(form).filterKeys { it in s.errors })
    }

    fun save() {
        val form = _state.value.form ?: return
        val errors = validate(form)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                profiles.save(form.toProfile())
                val target = form.targetWeightKg.takeIf { form.hasWeightGoal }
                val current = activeGoal
                when {
                    target == null && current != null -> goals.clearActiveGoal()
                    target != null && (current == null || current.targetWeightKg != target || current.reviewDate != form.reviewDate) ->
                        goals.setActiveGoal(target, form.reviewDate, latestWeightKg, today)
                }
            }.onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { _state.update { it.copy(saving = false, saveFailed = true) } }
        }
    }
}

@Composable
fun ProfileEditScreen(viewModel: ProfileEditViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    EditScaffold(
        title = stringResource(R.string.profile_edit_title),
        onBack = onDone,
        bottomBar = {
            if (state.saveFailed) FitNote(stringResource(R.string.error_save_failed), warning = true)
            FitPrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = !state.saving && state.form != null, modifier = Modifier.fillMaxWidth())
        },
    ) {
        val form = state.form ?: return@EditScaffold
        FitNote(stringResource(R.string.profile_edit_note))
        SectionTitle(stringResource(R.string.section_about))
        AboutFields(form, state.errors, viewModel.today, showWeight = false, onChange = viewModel::update)
        SectionTitle(stringResource(R.string.section_activity))
        ActivityFields(form, onChange = viewModel::update)
        SectionTitle(stringResource(R.string.section_goal))
        GoalFields(form, state.errors, viewModel.today, onChange = viewModel::update)
    }
}
