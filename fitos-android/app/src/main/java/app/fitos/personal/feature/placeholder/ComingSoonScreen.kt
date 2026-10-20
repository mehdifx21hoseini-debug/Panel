package app.fitos.personal.feature.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.fitos.core.designsystem.component.EmptyState
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.theme.FitTheme

/** A tab whose feature arrives in a later stage. Says so plainly instead of showing sample data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(@StringRes title: Int, emoji: FitEmoji, @StringRes heading: Int, @StringRes body: Int) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = FitTheme.colors.background,
                titleContentColor = FitTheme.colors.onSurface,
            ),
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            EmptyState(emoji = emoji, title = stringResource(heading), body = stringResource(body))
        }
    }
}
