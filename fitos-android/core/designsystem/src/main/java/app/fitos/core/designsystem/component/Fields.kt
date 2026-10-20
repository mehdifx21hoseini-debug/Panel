package app.fitos.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers

/**
 * Outlined text field with a visible label, an optional unit suffix and one line below it:
 * the error when there is one, otherwise the [hint].
 */
@Composable
fun FitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = FitTheme.colors
    val below = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it, color = colors.onSurfaceVariant) } },
        supportingText = below?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            errorContainerColor = colors.surface,
            cursorColor = colors.primary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (error != null) Modifier.semantics { error(error) } else Modifier),
    )
}

/**
 * Number field: accepts Persian, Arabic and Latin digits and shows them as Persian.
 * Read the value with PersianNumbers.parse.
 */
@Composable
fun FitNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    error: String? = null,
    hint: String? = null,
    allowDecimal: Boolean = true,
    maxIntegerDigits: Int = 4,
    maxFractionDigits: Int = 1,
    imeAction: ImeAction = ImeAction.Next,
) {
    FitTextField(
        value = value,
        onValueChange = { onValueChange(PersianNumbers.sanitizeInput(it, allowDecimal, maxIntegerDigits, maxFractionDigits)) },
        label = label,
        modifier = modifier,
        suffix = suffix,
        error = error,
        hint = hint,
        keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
        imeAction = imeAction,
    )
}
