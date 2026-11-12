package com.bdaniel.loyscan.ui.scanner

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.bdaniel.loyscan.ui.CARD_ASPECT_RATIO
import com.bdaniel.loyscan.ui.CardCutOutState
import com.bdaniel.loyscan.ui.cardCutOut
import com.bdaniel.loyscan.ui.scrimAround
import com.bdaniel.loyscan.ui.theme.Spacing

/** The scanner's card-shaped cut-out, so a test can find it. */
const val VIEWFINDER_TAG = "viewfinder"

/**
 * Drawn over the camera picture (#108): a dim scrim with a clear card-ratio cut-out where the card goes, and the [hint]
 * under it when there is one. Visual only: the camera still reads the whole picture, so a barcode outside the cut-out
 * scans too. The cut-out is as wide as fits, and shrinks when the hint needs the height.
 */
@Composable
fun Viewfinder(hint: String?, modifier: Modifier = Modifier) {
    val cutOut = remember { CardCutOutState() }
    Column(
        modifier.fillMaxSize().scrimAround(cutOut).padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Both weighted: when the height is short (a large font), each gets a share of it, the cut-out shrinks and the
        // hint scrolls rather than being cut off.
        Box(Modifier.weight(1f, fill = false).aspectRatio(CARD_ASPECT_RATIO).cardCutOut(cutOut).testTag(VIEWFINDER_TAG))
        if (hint != null) {
            Text(
                hint,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false).padding(top = Spacing.l).verticalScroll(rememberScrollState()),
            )
        }
    }
}
