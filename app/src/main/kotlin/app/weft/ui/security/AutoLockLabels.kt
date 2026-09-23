package app.weft.ui.security

import androidx.annotation.StringRes
import app.weft.R

/** Labels for the auto-lock choices (seconds; 0 = as soon as the user leaves). */
@StringRes fun autoLockOption(seconds: Int) = when (seconds) {
    0 -> R.string.autolock_now
    30 -> R.string.autolock_30
    60 -> R.string.autolock_60
    300 -> R.string.autolock_300
    else -> R.string.autolock_900
}

@StringRes fun autoLockValue(seconds: Int) = when (seconds) {
    0 -> R.string.autolock_value_now
    30 -> R.string.autolock_value_30
    60 -> R.string.autolock_value_60
    300 -> R.string.autolock_value_300
    else -> R.string.autolock_value_900
}
