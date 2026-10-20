package app.fitos.personal.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.fitos.core.designsystem.component.ChoiceCard
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitSegmented
import app.fitos.core.designsystem.component.FitStepper
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.component.JalaliDateField
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.HomeEquipment
import app.fitos.core.domain.profile.InputError
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.ProfileRules
import app.fitos.core.domain.profile.Sex
import app.fitos.core.domain.time.JalaliDate
import app.fitos.personal.R
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.message
import java.time.LocalDate

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = FitTheme.colors.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun FieldHint(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) FitTheme.colors.error else FitTheme.colors.onSurfaceVariant,
    )
}

/** Name, sex, birth date, height and (first run only) today's weight. */
@Composable
fun AboutFields(
    form: ProfileForm,
    errors: Map<ProfileField, InputError>,
    today: LocalDate,
    showWeight: Boolean,
    onChange: (ProfileForm.() -> ProfileForm) -> Unit,
) {
    val thisYear = JalaliDate.of(today).year
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s16)) {
        FitTextField(
            value = form.name,
            onValueChange = { v -> onChange { copy(name = v.take(30)) } },
            label = stringResource(R.string.field_name),
            hint = stringResource(R.string.field_name_hint),
        )
        Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FieldLabel(stringResource(R.string.field_sex))
            FitSegmented(
                options = listOf(Sex.MALE, Sex.FEMALE),
                selected = form.sex,
                onSelect = { v -> onChange { copy(sex = v) } },
                label = { stringResource(if (it == Sex.MALE) R.string.sex_male else R.string.sex_female) },
            )
            val sexError = errors[ProfileField.SEX]
            FieldHint(sexError?.message() ?: stringResource(R.string.field_sex_hint), isError = sexError != null)
        }
        JalaliDateField(
            date = form.birthDate,
            onDateChange = { v -> onChange { copy(birthDate = v) } },
            label = stringResource(R.string.field_birth_date),
            years = (thisYear - ProfileRules.AGE_YEARS.last)..(thisYear - ProfileRules.AGE_YEARS.first),
            error = errors[ProfileField.BIRTH_DATE]?.message(),
        )
        FitNumberField(
            value = form.height,
            onValueChange = { v -> onChange { copy(height = v) } },
            label = stringResource(R.string.field_height),
            suffix = stringResource(R.string.unit_cm),
            maxIntegerDigits = 3,
            error = errors[ProfileField.HEIGHT]?.message(),
        )
        if (showWeight) {
            FitNumberField(
                value = form.weight,
                onValueChange = { v -> onChange { copy(weight = v) } },
                label = stringResource(R.string.field_weight),
                suffix = stringResource(R.string.unit_kg),
                maxIntegerDigits = 3,
                hint = stringResource(R.string.field_weight_hint),
                error = errors[ProfileField.WEIGHT]?.message(),
            )
        }
    }
}

private val ActivityLevel.title: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary
        ActivityLevel.LIGHT -> R.string.activity_light
        ActivityLevel.MODERATE -> R.string.activity_moderate
        ActivityLevel.ACTIVE -> R.string.activity_active
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active
    }

private val ActivityLevel.body: Int
    get() = when (this) {
        ActivityLevel.SEDENTARY -> R.string.activity_sedentary_body
        ActivityLevel.LIGHT -> R.string.activity_light_body
        ActivityLevel.MODERATE -> R.string.activity_moderate_body
        ActivityLevel.ACTIVE -> R.string.activity_active_body
        ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active_body
    }

@Composable
fun ActivityLevel.label(): String = stringResource(title)

private val HomeEquipment.label: Int
    get() = when (this) {
        HomeEquipment.DUMBBELLS -> R.string.equipment_dumbbells
        HomeEquipment.BANDS -> R.string.equipment_bands
        HomeEquipment.PULL_UP_BAR -> R.string.equipment_pull_up_bar
        HomeEquipment.KETTLEBELL -> R.string.equipment_kettlebell
        HomeEquipment.BENCH -> R.string.equipment_bench
    }

/** Weekly sessions, overall activity level, usual time, short home workout and equipment. */
@Composable
fun ActivityFields(form: ProfileForm, onChange: (ProfileForm.() -> ProfileForm) -> Unit) {
    val suggested = ActivityLevel.suggestedFor(form.trainingDays)
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s16)) {
        FitStepper(
            label = stringResource(R.string.field_training_days),
            valueText = Fmt.int(form.trainingDays),
            onDecrease = { onChange { copy(trainingDays = trainingDays - 1) } },
            onIncrease = { onChange { copy(trainingDays = trainingDays + 1) } },
            canDecrease = form.trainingDays > ProfileRules.TRAINING_DAYS.first,
            canIncrease = form.trainingDays < ProfileRules.TRAINING_DAYS.last,
            decreaseLabel = stringResource(R.string.action_decrease),
            increaseLabel = stringResource(R.string.action_increase),
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FieldLabel(stringResource(R.string.field_activity))
            ActivityLevel.entries.forEach { level ->
                val title = stringResource(level.title) +
                    if (level == suggested) " · " + stringResource(R.string.activity_suggested) else ""
                ChoiceCard(
                    selected = form.effectiveActivity == level,
                    onClick = { onChange { copy(activity = level) } },
                    title = title,
                    body = stringResource(level.body),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FieldLabel(stringResource(R.string.field_training_time))
            FitChips(
                options = TrainingTime.entries,
                selected = setOf(form.trainingTime),
                onToggle = { v -> onChange { copy(trainingTime = v) } },
                label = { stringResource(it.label) },
                multi = false,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FieldLabel(stringResource(R.string.field_home_minutes))
            FitChips(
                options = HomeMinuteOptions,
                selected = setOf(form.homeMinutes),
                onToggle = { v -> onChange { copy(homeMinutes = v) } },
                label = { stringResource(R.string.minutes_value, Fmt.int(it)) },
                multi = false,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FieldLabel(stringResource(R.string.field_equipment))
            FitChips(
                options = HomeEquipment.entries,
                selected = form.equipment,
                onToggle = { v -> onChange { copy(equipment = if (v in equipment) equipment - v else equipment + v) } },
                label = { stringResource(it.label) },
            )
            FieldHint(stringResource(R.string.field_equipment_hint))
        }
    }
}

private val PrimaryGoal.title: Int
    get() = when (this) {
        PrimaryGoal.FAT_LOSS -> R.string.goal_fat_loss
        PrimaryGoal.MAINTAIN -> R.string.goal_maintain
        PrimaryGoal.MUSCLE_GAIN -> R.string.goal_muscle_gain
    }

private val PrimaryGoal.body: Int
    get() = when (this) {
        PrimaryGoal.FAT_LOSS -> R.string.goal_fat_loss_body
        PrimaryGoal.MAINTAIN -> R.string.goal_maintain_body
        PrimaryGoal.MUSCLE_GAIN -> R.string.goal_muscle_gain_body
    }

private val PrimaryGoal.emoji: FitEmoji
    get() = when (this) {
        PrimaryGoal.FAT_LOSS -> FitEmoji.FIRE
        PrimaryGoal.MAINTAIN -> FitEmoji.SCALE
        PrimaryGoal.MUSCLE_GAIN -> FitEmoji.BICEPS
    }

private val EnergyPace.label: Int
    get() = when (this) {
        EnergyPace.GENTLE -> R.string.pace_gentle
        EnergyPace.STANDARD -> R.string.pace_standard
        EnergyPace.FIRM -> R.string.pace_firm
    }

/** Main goal, deficit pace (fat loss only), optional target weight and its review date. */
@Composable
fun GoalFields(
    form: ProfileForm,
    errors: Map<ProfileField, InputError>,
    today: LocalDate,
    onChange: (ProfileForm.() -> ProfileForm) -> Unit,
) {
    val thisYear = JalaliDate.of(today).year
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s16)) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            PrimaryGoal.entries.forEach { goal ->
                ChoiceCard(
                    selected = form.goal == goal,
                    onClick = { onChange { copy(goal = goal) } },
                    title = stringResource(goal.title),
                    body = stringResource(goal.body),
                    emoji = goal.emoji,
                )
            }
        }
        if (form.goal == PrimaryGoal.FAT_LOSS) {
            Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                FieldLabel(stringResource(R.string.field_pace))
                FitSegmented(
                    options = EnergyPace.entries,
                    selected = form.pace,
                    onSelect = { v -> onChange { copy(pace = v) } },
                    label = { stringResource(it.label) },
                )
                FieldHint(stringResource(R.string.pace_hint))
            }
        }
        if (form.hasWeightGoal) {
            FitNumberField(
                value = form.targetWeight,
                onValueChange = { v -> onChange { copy(targetWeight = v) } },
                label = stringResource(R.string.field_target_weight) + " (" + stringResource(R.string.optional) + ")",
                suffix = stringResource(R.string.unit_kg),
                maxIntegerDigits = 3,
                hint = stringResource(R.string.field_target_weight_hint),
                error = errors[ProfileField.TARGET_WEIGHT]?.message(),
            )
            if (form.targetWeight.isNotBlank()) {
                JalaliDateField(
                    date = form.reviewDate,
                    onDateChange = { v -> onChange { copy(reviewDate = v) } },
                    label = stringResource(R.string.field_review_date),
                    years = thisYear..(thisYear + 3),
                    hint = stringResource(R.string.field_review_date_hint),
                    error = errors[ProfileField.REVIEW_DATE]?.message(),
                )
            }
        }
    }
}
