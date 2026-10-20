package app.fitos.personal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fitos.core.domain.profile.InputError
import app.fitos.personal.R

/** Persian text for a rejected input. [range] fills the generic out-of-range message. */
@Composable
fun InputError.message(range: Pair<String, String>? = null): String = when (this) {
    InputError.REQUIRED -> stringResource(R.string.error_required)
    InputError.HEIGHT_RANGE -> stringResource(R.string.error_height)
    InputError.WEIGHT_RANGE -> stringResource(R.string.error_weight)
    InputError.AGE_RANGE -> stringResource(R.string.error_age)
    InputError.TARGET_WEIGHT_RANGE -> stringResource(R.string.error_target_weight)
    InputError.TARGET_NOT_BELOW_CURRENT -> stringResource(R.string.error_target_not_below)
    InputError.TARGET_NOT_ABOVE_CURRENT -> stringResource(R.string.error_target_not_above)
    InputError.REVIEW_DATE_NOT_FUTURE -> stringResource(R.string.error_review_date)
    InputError.OUT_OF_RANGE -> stringResource(R.string.error_range, range?.first ?: "", range?.second ?: "")
}
