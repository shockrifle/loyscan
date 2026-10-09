package com.bdaniel.loyscan.ui

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

/** Parses a brand colour such as `#RRGGBB`; `null` when it is empty or malformed, so callers pick a fallback. */
fun String.toBrandColorOrNull(): Color? =
    try {
        Color(toColorInt())
    } catch (_: IllegalArgumentException) {
        null // Malformed, for example "not a colour".
    } catch (_: IndexOutOfBoundsException) {
        null // Empty.
    }
