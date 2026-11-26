package com.bdaniel.loyscan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.domain.storeInitials
import com.bdaniel.loyscan.ui.theme.CardShape

/** The store's logo preview ([LogoPreview]): the store logo screen's, and the custom store form's logo slot. */
const val STORE_LOGO_PREVIEW_TAG = "storeLogoPreview"

/**
 * The store as its cards' tiles show it (#192): its unlabelled tile, the [logo] on the brand [color] drawn with its
 * [logoTreatment] (#240), else the initials
 * of its [name]. On the store logo screen and in the custom store form's logo slot (#151), there drawn with the form's
 * name and colour as typed. TalkBack reads it as the store's name, as it reads a tile.
 */
@Composable
fun LogoPreview(name: String, color: String, logo: String?, logoTreatment: LogoTreatment = LogoTreatment.AS_IS) {
    Box(
        Modifier
            .clip(CardShape) // the face draws the colour; the list's Card clips its corners, here this does
            .semantics(mergeDescendants = true) { contentDescription = name }
            .testTag(STORE_LOGO_PREVIEW_TAG),
    ) {
        CardTileFace(color = color, initials = storeInitials(name), logo = logo, logoTreatment = logoTreatment)
    }
}
