package com.bdaniel.loyscan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The theme's shapes (#108): Material 3's default values, set explicitly so a change is one place. */
val LoyScanShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The fraction of a card's short side its corner radius is: an ID-1 card has 3.18 mm corners on a 53.98 mm side. */
const val CARD_CORNER_FRACTION = 0.06f

/**
 * The shape of every depiction of the physical card (#108): list tiles, photo previews, the crop frame, the scanner's
 * cut-out. Its corners are a share of the short side, so the card looks the same at every size.
 */
val CardShape = RoundedCornerShape(percent = (CARD_CORNER_FRACTION * 100).toInt())
