package com.bdaniel.loyscan.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

/**
 * Black or white, whichever contrasts more with this colour. By the WCAG contrast ratio, `(L1 + 0.05) / (L2 + 0.05)`,
 * black wins above a relative luminance of about 0.18, not 0.5.
 */
fun Color.contrastingTextColor(): Color {
    val luminance = luminance()
    val withBlack = (luminance + 0.05f) / 0.05f
    val withWhite = 1.05f / (luminance + 0.05f)
    return if (withBlack > withWhite) Color.Black else Color.White
}
