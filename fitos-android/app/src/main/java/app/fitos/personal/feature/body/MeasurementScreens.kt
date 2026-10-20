package app.fitos.personal.feature.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import app.fitos.core.designsystem.component.ChartPoint
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.component.JalaliDateField
import app.fitos.core.designsystem.component.TrendChart
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.body.BodyMeasurement
import app.fitos.core.domain.body.MEASUREMENT_CM
import app.fitos.core.domain.body.MeasurementRepository
import app.fitos.core.domain.body.MeasurementSite
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliDate
import app.fitos.personal.R
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeasurementsState(
    val loaded: Boolean = false,
    val sites: List<MeasurementSite> = emptyList(),
    val values: List<BodyMeasurement> = emptyList(),
)

/** Shared by the entry screen and the per-place history screen. */
class MeasurementsViewModel(private val repo: MeasurementRepository, private val clock: Clock) : ViewModel() {
    fun today(): LocalDate = LocalDate.now(clock)

    val state: StateFlow<MeasurementsState> = combine(repo.sites, repo.all) { sites, values -> MeasurementsState(true, sites, values) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementsState())

    fun add(day: LocalDate, valuesCm: Map<String, Double>, note: String?, done: () -> Unit) {
        viewModelScope.launch {
            repo.add(day, valuesCm, note)
            done()
        }
    }

    fun update(id: String, day: LocalDate, valueCm: Double) = viewModelScope.launch { repo.update(id, day, valueCm, null) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun addSite(name: String) = viewModelScope.launch { repo.addSite(name) }
    fun setHidden(site: MeasurementSite, hidden: Boolean) = viewModelScope.launch { repo.setHidden(site.id, hidden) }
}

/** One field per visible place; only filled places are saved. Places can be added or hidden here. */
@Composable
fun MeasurementsEntryScreen(viewModel: MeasurementsViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { viewModel.today() }
    var day by remember { mutableStateOf(today) }
    val inputs = remember { mutableStateMapOf<String, String>() }
    var note by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val visible = state.sites.filter { !it.isHidden }
    val parsed = visible.mapNotNull { site -> inputs[site.id]?.takeIf { it.isNotBlank() }?.let { site.id to PersianNumbers.parse(it) } }
    val invalid = parsed.filter { (_, v) -> v == null || v !in MEASUREMENT_CM }.map { it.first }.toSet()

    EditScaffold(
        title = stringResource(R.string.measurements_entry_title),
        onBack = onDone,
        bottomBar = {
            if (showErrors && parsed.isEmpty()) FitNote(stringResource(R.string.measurements_none_filled), warning = true)
            FitPrimaryButton(
                stringResource(R.string.action_save),
                onClick = {
                    showErrors = true
                    if (parsed.isNotEmpty() && invalid.isEmpty()) {
                        viewModel.add(day, parsed.associate { it.first to it.second!! }, note, onDone)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        FitNote(stringResource(R.string.measurements_entry_hint))
        JalaliDateField(
            date = day,
            onDateChange = { day = it },
            label = stringResource(R.string.field_date),
            years = (JalaliDate.of(today).year - 5)..JalaliDate.of(today).year,
            latest = today,
        )
        visible.forEach { site ->
            FitNumberField(
                value = inputs[site.id].orEmpty(),
                onValueChange = { inputs[site.id] = it },
                label = site.name,
                suffix = stringResource(R.string.unit_cm),
                maxIntegerDigits = 3,
                error = if (showErrors && site.id in invalid) stringResource(R.string.measurement_range) else null,
            )
        }
        FitTextField(value = note, onValueChange = { note = it.take(200) }, label = stringResource(R.string.field_note) + " (" + stringResource(R.string.optional) + ")")

        SectionTitle(stringResource(R.string.sites_manage))
        FitCard {
            state.sites.forEach { site ->
                Row(Modifier.fillMaxWidth().heightIn(min = FitSpacing.target), verticalAlignment = Alignment.CenterVertically) {
                    Text(site.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.site_hidden), style = MaterialTheme.typography.labelMedium, color = FitTheme.colors.onSurfaceVariant)
                    Switch(
                        checked = site.isHidden,
                        onCheckedChange = { viewModel.setHidden(site, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = FitTheme.colors.primary),
                        modifier = Modifier.padding(start = FitSpacing.s8),
                    )
                }
            }
            FitSecondaryButton(stringResource(R.string.site_add), onClick = { adding = true }, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.site_add)) },
            text = { FitTextField(value = name, onValueChange = { name = it.take(40) }, label = stringResource(R.string.site_name)) },
            confirmButton = {
                FitTextButton(stringResource(R.string.action_save), onClick = {
                    if (name.isNotBlank()) viewModel.addSite(name)
                    adding = false
                })
            },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { adding = false }) },
        )
    }
}

/** History of one place: small chart, then every value (tap to edit or delete). */
@Composable
fun SiteHistoryScreen(viewModel: MeasurementsViewModel, siteId: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val site = state.sites.firstOrNull { it.id == siteId }
    val values = state.values.filter { it.siteId == siteId }
    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }
    val colors = FitTheme.colors

    EditScaffold(title = site?.name ?: stringResource(R.string.measurements_title), onBack = onBack) {
        if (state.loaded && values.isEmpty()) {
            Text(stringResource(R.string.site_history_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            return@EditScaffold
        }
        FitCard {
            if (values.size < 3) {
                FitNote(stringResource(R.string.chart_not_enough_short))
            } else {
                TrendChart(
                    points = values.map { ChartPoint(it.day.toEpochDay(), it.valueCm, it.valueCm) },
                    description = "${site?.name}: ${Fmt.kg(values.first().valueCm)} → ${Fmt.kg(values.last().valueCm)}",
                    valueLabel = { Fmt.kg(it) },
                    dateLabel = { DateFormats.dayMonth(LocalDate.ofEpochDay(it)) },
                    tooltip = { "${DateFormats.dayMonth(LocalDate.ofEpochDay(it.x))} · ${Fmt.kg(it.value)}" },
                    height = 160.dp,
                )
            }
        }
        values.asReversed().forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { editing = m }
                    .padding(horizontal = FitSpacing.s4, vertical = FitSpacing.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormats.longDate(m.day), style = MaterialTheme.typography.bodyMedium)
                    m.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.subtle) }
                }
                Text(stringResource(R.string.value_cm, Fmt.kg(m.valueCm)), style = numeralStyle(16), color = colors.onSurface)
            }
        }
    }

    editing?.let { m ->
        var text by remember(m.id) { mutableStateOf(Fmt.field(m.valueCm)) }
        var day by remember(m.id) { mutableStateOf(m.day) }
        var confirmDelete by remember(m.id) { mutableStateOf(false) }
        val value = PersianNumbers.parse(text)
        val ok = value != null && value in MEASUREMENT_CM
        val today = viewModel.today()
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(R.string.measurement_delete_confirm)) },
                text = { Text("${site?.name}: ${stringResource(R.string.value_cm, Fmt.kg(m.valueCm))}، ${DateFormats.longDate(m.day)}") },
                confirmButton = {
                    FitTextButton(stringResource(R.string.action_delete), onClick = {
                        viewModel.delete(m.id)
                        editing = null
                    })
                },
                dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { confirmDelete = false }) },
            )
        } else {
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(R.string.measurement_edit)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                        FitNumberField(
                            value = text,
                            onValueChange = { text = it },
                            label = site?.name.orEmpty(),
                            suffix = stringResource(R.string.unit_cm),
                            maxIntegerDigits = 3,
                            error = if (!ok) stringResource(R.string.measurement_range) else null,
                        )
                        JalaliDateField(
                            date = day,
                            onDateChange = { day = it },
                            label = stringResource(R.string.field_date),
                            years = (JalaliDate.of(today).year - 5)..JalaliDate.of(today).year,
                            latest = today,
                        )
                    }
                },
                confirmButton = {
                    FitTextButton(stringResource(R.string.action_save), onClick = {
                        if (ok) {
                            viewModel.update(m.id, day, value!!)
                            editing = null
                        }
                    })
                },
                dismissButton = { FitTextButton(stringResource(R.string.action_delete), onClick = { confirmDelete = true }) },
            )
        }
    }
}
