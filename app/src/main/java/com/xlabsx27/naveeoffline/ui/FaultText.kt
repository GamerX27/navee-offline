package com.xlabsx27.naveeoffline.ui

import androidx.annotation.StringRes
import com.xlabsx27.naveeoffline.R

/** Fault number → its description. Explicit so resource shrinking keeps every string. */
@StringRes
fun faultText(number: Int): Int? = when (number) {
    10 -> R.string.fault_10
    11 -> R.string.fault_11
    12 -> R.string.fault_12
    14 -> R.string.fault_14
    15 -> R.string.fault_15
    18 -> R.string.fault_18
    21 -> R.string.fault_21
    24 -> R.string.fault_24
    28 -> R.string.fault_28
    29 -> R.string.fault_29
    39 -> R.string.fault_39
    40 -> R.string.fault_40
    43 -> R.string.fault_43
    45 -> R.string.fault_45
    50 -> R.string.fault_50
    52 -> R.string.fault_52
    53 -> R.string.fault_53
    54 -> R.string.fault_54
    55 -> R.string.fault_55
    56 -> R.string.fault_56
    57 -> R.string.fault_57
    60 -> R.string.fault_60
    61 -> R.string.fault_61
    else -> null
}
