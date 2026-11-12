package com.bdaniel.loyscan.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale every screen and the widget use (#108), in place of `.dp` literals: padding, gaps, insets. A plain
 * object, not a `CompositionLocal`, so the Glance widget reads the same values. Which step goes where:
 * `wiki/design-system.md`. A size that is no step of the scale is a named constant next to its screen.
 */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
