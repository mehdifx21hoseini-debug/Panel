package app.fitos.personal.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.personal.R
import app.fitos.personal.feature.profile.AboutFields
import app.fitos.personal.feature.profile.ActivityFields
import app.fitos.personal.feature.profile.GoalFields
import app.fitos.personal.feature.targets.AssumptionsCard
import app.fitos.personal.feature.targets.TargetFields
import app.fitos.personal.ui.Fmt

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onFinished() }
    BackHandler(enabled = state.step != OnboardingStep.WELCOME) { viewModel.back() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        if (state.step != OnboardingStep.WELCOME) StepHeader(state.step, onBack = viewModel::back)

        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
                verticalArrangement = Arrangement.spacedBy(FitSpacing.s16),
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> Welcome()
                    OnboardingStep.ABOUT -> {
                        StepTitle(R.string.about_title, R.string.about_subtitle)
                        AboutFields(state.form, state.errors, viewModel.today, showWeight = true, onChange = viewModel::updateForm)
                    }
                    OnboardingStep.ACTIVITY -> {
                        StepTitle(R.string.activity_title, R.string.activity_subtitle)
                        ActivityFields(state.form, onChange = viewModel::updateForm)
                    }
                    OnboardingStep.GOAL -> {
                        StepTitle(R.string.goal_title, R.string.goal_subtitle)
                        GoalFields(state.form, state.errors, viewModel.today, onChange = viewModel::updateForm)
                    }
                    OnboardingStep.TARGETS -> state.targets?.let { targets ->
                        StepTitle(R.string.targets_title, R.string.targets_subtitle)
                        TargetFields(targets, state.targetErrors, onChange = viewModel::updateTargets)
                        if (targets.isEdited) FitTextButton(stringResource(R.string.targets_reset), onClick = viewModel::resetTargets)
                        targets.base.assumptions?.let { AssumptionsCard(it) }
                    }
                }
                Spacer(Modifier.height(FitSpacing.s8))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s12),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s8),
        ) {
            if (state.saveFailed) FitNote(stringResource(R.string.error_save_failed), warning = true)
            FitPrimaryButton(
                text = stringResource(
                    when (state.step) {
                        OnboardingStep.WELCOME -> R.string.welcome_start
                        OnboardingStep.TARGETS -> R.string.targets_finish
                        else -> R.string.action_continue
                    },
                ),
                onClick = viewModel::next,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val StepCount = OnboardingStep.entries.size - 1

@Composable
private fun StepHeader(step: OnboardingStep, onBack: () -> Unit) {
    val colors = FitTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = FitSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(FitIcons.Back, contentDescription = stringResource(R.string.action_back), tint = colors.onSurface)
        }
        LinearProgressIndicator(
            progress = { step.ordinal / StepCount.toFloat() },
            color = colors.primary,
            trackColor = colors.track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.weight(1f).height(6.dp),
        )
        Text(
            stringResource(R.string.step_of, Fmt.int(step.ordinal), Fmt.int(StepCount)),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FitSpacing.s12),
        )
    }
}

@Composable
private fun StepTitle(title: Int, subtitle: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = FitTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun Welcome() {
    val colors = FitTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = FitSpacing.s48),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s16),
    ) {
        Box(contentAlignment = Alignment.Center) {
            EmojiTile(FitEmoji.LIFTING, size = 112.dp, tint = colors.primaryContainer)
        }
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FitCard {
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
                EmojiTile(FitEmoji.LOCK, size = 40.dp, tint = colors.habits.container)
                Text(stringResource(R.string.welcome_privacy), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

