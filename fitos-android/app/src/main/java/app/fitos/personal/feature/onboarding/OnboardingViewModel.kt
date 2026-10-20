package app.fitos.personal.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fitos.core.domain.nutrition.TargetCalculator
import app.fitos.core.domain.nutrition.TargetInput
import app.fitos.core.domain.profile.InitialSetup
import app.fitos.core.domain.profile.InputError
import app.fitos.core.domain.profile.SetupRepository
import app.fitos.personal.feature.profile.ProfileField
import app.fitos.personal.feature.profile.ProfileForm
import app.fitos.personal.feature.targets.TargetField
import app.fitos.personal.feature.targets.TargetsForm
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, ABOUT, ACTIVITY, GOAL, TARGETS }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val form: ProfileForm = ProfileForm(),
    val errors: Map<ProfileField, InputError> = emptyMap(),
    val targets: TargetsForm? = null,
    val targetErrors: Map<TargetField, InputError> = emptyMap(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val done: Boolean = false,
)

/**
 * First-run setup. Errors appear only after "continue" is pressed and clear as soon as the
 * field changes; nothing is written until the last step, and then in one transaction.
 */
class OnboardingViewModel(private val setup: SetupRepository, clock: Clock) : ViewModel() {

    val today: LocalDate = LocalDate.now(clock)

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun updateForm(change: ProfileForm.() -> ProfileForm) = _state.update { s ->
        val form = s.form.change()
        // Revalidate only the fields that already show an error, so fixing one clears it at once.
        val stillWrong = (form.aboutErrors(today, requireWeight = true) + form.goalErrors(today, form.weightKg))
            .filterKeys { it in s.errors }
        s.copy(form = form, errors = stillWrong)
    }

    fun updateTargets(change: TargetsForm.() -> TargetsForm) = _state.update { s ->
        val targets = s.targets?.change() ?: return@update s
        s.copy(targets = targets, targetErrors = targets.errors().filterKeys { it in s.targetErrors })
    }

    fun resetTargets() = _state.update { s -> s.targets?.let { s.copy(targets = TargetsForm.from(it.base), targetErrors = emptyMap()) } ?: s }

    fun next() {
        val s = _state.value
        when (s.step) {
            OnboardingStep.WELCOME -> go(OnboardingStep.ABOUT)
            OnboardingStep.ABOUT -> {
                val errors = s.form.aboutErrors(today, requireWeight = true)
                if (errors.isEmpty()) go(OnboardingStep.ACTIVITY) else _state.update { it.copy(errors = errors) }
            }
            OnboardingStep.ACTIVITY -> {
                _state.update { it.copy(form = it.form.copy(reviewDate = it.form.reviewDate ?: today.plusWeeks(12))) }
                go(OnboardingStep.GOAL)
            }
            OnboardingStep.GOAL -> {
                val errors = s.form.goalErrors(today, s.form.weightKg)
                if (errors.isNotEmpty()) {
                    _state.update { it.copy(errors = errors) }
                } else {
                    // A fresh suggestion every time the goal step is passed, since inputs may have changed.
                    _state.update { it.copy(targets = TargetsForm.from(suggest(it.form)), targetErrors = emptyMap()) }
                    go(OnboardingStep.TARGETS)
                }
            }
            OnboardingStep.TARGETS -> save()
        }
    }

    fun back() {
        val s = _state.value
        val previous = OnboardingStep.entries.getOrNull(s.step.ordinal - 1) ?: return
        _state.update { it.copy(step = previous, errors = emptyMap(), targetErrors = emptyMap()) }
    }

    private fun go(step: OnboardingStep) = _state.update { it.copy(step = step, errors = emptyMap()) }

    private fun suggest(form: ProfileForm) = TargetCalculator.suggest(
        TargetInput(
            sex = form.sex!!,
            ageYears = form.toProfile().ageOn(today),
            weightKg = form.weightKg!!,
            heightCm = form.heightCm!!,
            activity = form.effectiveActivity,
            goal = form.goal,
            pace = form.pace,
            targetWeightKg = form.targetWeightKg.takeIf { form.hasWeightGoal },
        ),
        effectiveFrom = today,
    )

    private fun save() {
        val s = _state.value
        val targets = s.targets ?: return
        val errors = targets.errors()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(targetErrors = errors) }
            return
        }
        if (s.saving) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            val form = s.form
            val target = form.targetWeightKg.takeIf { form.hasWeightGoal }
            runCatching {
                setup.completeSetup(
                    InitialSetup(
                        profile = form.toProfile(),
                        currentWeightKg = form.weightKg!!,
                        targetWeightKg = target,
                        goalReviewDate = form.reviewDate.takeIf { target != null },
                        target = targets.toTarget(today),
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, done = true) }
            }.onFailure {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}
