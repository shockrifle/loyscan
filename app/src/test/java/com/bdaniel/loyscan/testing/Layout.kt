package com.bdaniel.loyscan.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult

/**
 * Fails when a text on screen is clipped or runs into another one (#108, checked at font scale 2 on a 320dp screen):
 * every text leaf lies inside the root horizontally, shows all of its text (no visual overflow), and overlaps no other
 * text. Horizontal only: a text scrolled below the screen is not clipped. Text fields' input is skipped (a field's
 * label floats over its border by design). [fitsByDesign] lists texts that are meant to end in "…" when they don't fit,
 * and auto-sized ones: their semantics report the layout at the largest size, not the size they are drawn at.
 * The test class needs `@GraphicsMode(NATIVE)`: in Robolectric's default mode every text measures a few pixels wide.
 */
fun SemanticsNodeInteractionsProvider.assertTextFitsAndDoesNotOverlap(fitsByDesign: Set<String> = emptySet()) {
    val root = onRoot().fetchSemanticsNode().boundsInRoot
    val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .filter { it.config.getOrNull(SemanticsProperties.EditableText) == null }
        .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
    val problems = mutableListOf<String>()
    texts.forEach { node ->
        val text = node.text()
        val bounds = node.boundsInRoot
        if (bounds.left < root.left - SLACK || bounds.right > root.right + SLACK) {
            problems += "\"$text\" runs off the screen: $bounds, screen $root"
        }
        if (text !in fitsByDesign && node.layout()?.cutsOff() == true) problems += "\"$text\" is clipped"
    }
    texts.forEachIndexed { i, a ->
        texts.drop(i + 1).forEach { b ->
            val overlap = a.boundsInRoot.intersect(b.boundsInRoot)
            if (overlap.width > SLACK && overlap.height > SLACK) problems += "\"${a.text()}\" overlaps \"${b.text()}\""
        }
    }
    if (problems.isNotEmpty()) throw AssertionError(problems.joinToString("\n"))
}

/** Half a pixel either way: bounds are rounded. */
private const val SLACK = 0.5f

private fun SemanticsNode.text() = config[SemanticsProperties.Text].joinToString(" ")

private fun SemanticsNode.layout(): TextLayoutResult? {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.firstOrNull()
}

/**
 * Whether the text doesn't all show: it has more lines than it may, a line ends in "…", or a line runs past the text's
 * own width. Not `hasVisualOverflow`, which compares with the width the text was offered, not the width it took.
 */
private fun TextLayoutResult.cutsOff() =
    didOverflowHeight || (0 until lineCount).any { isLineEllipsized(it) || getLineRight(it) > size.width + SLACK }

/** Whether [this] lies within [outer], to half a pixel. */
fun Rect.isWithin(outer: Rect) =
    left >= outer.left - SLACK && top >= outer.top - SLACK && right <= outer.right + SLACK && bottom <= outer.bottom + SLACK

/** [content] at the user's font scale [fontScale] (Settings → Font size), the screen's density unchanged. */
@Composable
fun WithFontScale(fontScale: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale), content = content)
}
