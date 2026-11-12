package com.bdaniel.loyscan.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.bdaniel.loyscan.ui.theme.CardShape

/** How much a card cut-out dims the camera picture or photo around it. */
val CUT_OUT_SCRIM = Color.Black.copy(alpha = 0.6f)

/** The white line around a card cut-out. */
val CUT_OUT_OUTLINE = 2.dp

/**
 * Where a card cut-out (the scanner's viewfinder, the crop frame, #108) is, so the scrim around it leaves it clear. The
 * scrim is drawn by one node ([scrimAround]) and the cut-out laid out by another ([cardCutOut]), so each records its
 * place in the root.
 */
@Stable
class CardCutOutState {
    internal var scrim by mutableStateOf(Offset.Zero)
    internal var hole by mutableStateOf<Rect?>(null)
}

/** Dims this node's area, except the [CardShape] hole at the [cardCutOut] of [state]; nothing until that is placed. */
fun Modifier.scrimAround(state: CardCutOutState): Modifier =
    onGloballyPositioned { state.scrim = it.positionInRoot() }
        .drawBehind {
            val hole = state.hole?.translate(-state.scrim) ?: return@drawBehind
            val path = Path().apply {
                addOutline(CardShape.createOutline(hole.size, layoutDirection, this@drawBehind))
                translate(hole.topLeft)
            }
            clipPath(path, ClipOp.Difference) { drawRect(CUT_OUT_SCRIM) }
        }

/** The card cut-out itself: a white [CardShape] outline, its place recorded in [state] for the scrim. */
fun Modifier.cardCutOut(state: CardCutOutState): Modifier =
    onGloballyPositioned { state.hole = it.boundsInRoot() }
        .border(CUT_OUT_OUTLINE, Color.White, CardShape)
