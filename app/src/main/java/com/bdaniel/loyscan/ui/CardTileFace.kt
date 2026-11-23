package com.bdaniel.loyscan.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import coil.compose.AsyncImage
import com.bdaniel.loyscan.ui.theme.Spacing

/** A tile's front photo (#10). */
const val CARD_TILE_PHOTO_TAG = "cardTilePhoto"

/** A tile's store logo (#151), shown when the card has no front photo. */
const val CARD_TILE_LOGO_TAG = "cardTileLogo"

/**
 * What a card's tile draws (#192): the brand [color] at card proportions, and on it the front [photo], else the store's
 * [logo] at [TILE_LOGO_FRACTION], else the store's [initials]; the card's [label], if any, along the bottom edge. The
 * My cards tile ([com.bdaniel.loyscan.CardItem]) and the store's logo preview ([LogoPreview]) both draw this, so they
 * can't drift apart.
 *
 * [modifier] comes after the colour and the proportions: the list's tile hangs its shared element on it.
 */
@Composable
fun CardTileFace(
    color: String,
    initials: String,
    logo: String?,
    modifier: Modifier = Modifier,
    photo: String? = null,
    label: String? = null,
) {
    val cardColor = color.toBrandColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant
    // A photo that can't be loaded (a broken file) falls back to the initials, not a bare brand-colour box. While it
    // loads, the brand colour shows: initials there would flash on every tile that has a photo.
    var photoFailed by remember(photo) { mutableStateOf(false) }
    val shownPhoto = photo?.takeUnless { photoFailed }
    // The store's logo (#151) when it loads; one that can't be loaded shows the initials, as a missing one does.
    var logoFailed by remember(logo) { mutableStateOf(false) }
    val shownLogo = logo?.takeUnless { logoFailed }
    // On a photo or a logo, the label sits on a dark scrim over the picture, so it is white whatever the picture's
    // colours (review of #183: a centred logo reaches under the label).
    val onPicture = shownPhoto != null || shownLogo != null
    val textColor = if (onPicture) Color.White else cardColor.contrastingTextColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardColor)
            .aspectRatio(CARD_ASPECT_RATIO)
            .then(modifier),
    ) {
        // The front photo, when the card has one, is the tile: our answer to not bundling store logos (#10).
        if (shownPhoto != null) {
            AsyncImage(
                model = CardPhoto(shownPhoto),
                contentDescription = null, // The tile is described as a whole.
                imageLoader = hiltImageLoader(),
                contentScale = ContentScale.Crop,
                onError = { photoFailed = true },
                modifier = Modifier.fillMaxSize().testTag(CARD_TILE_PHOTO_TAG),
            )
        }
        // The logo, like the photo, is centred in the whole tile, at a size that doesn't depend on the label (#183).
        if (shownPhoto == null && shownLogo != null) {
            Box(Modifier.matchParentSize().padding(horizontal = Spacing.s), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = CardPhoto(shownLogo),
                    contentDescription = null, // The tile is described as a whole.
                    imageLoader = hiltImageLoader(),
                    onError = { logoFailed = true },
                    modifier = Modifier.fillMaxSize(TILE_LOGO_FRACTION).testTag(CARD_TILE_LOGO_TAG),
                )
            }
        }
        // Only the card's own label, never the store's name: the logo, the photo or the initials say which store
        // it is (#183). TalkBack hears the store from the caller's description.
        val labelContent: (@Composable () -> Unit)? = label?.let { text ->
            {
                Column(Modifier.fillMaxWidth()) {
                    // On a picture, the scrim fades in above the label and is at full strength behind the text.
                    if (onPicture) Spacer(Modifier.fillMaxWidth().height(Spacing.l).background(PHOTO_SCRIM_FADE))
                    Text(
                        text = text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (onPicture) Modifier.background(PHOTO_LABEL_SCRIM) else Modifier)
                            .padding(start = Spacing.s, end = Spacing.s, bottom = Spacing.s),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        CentredOverLabel(label = labelContent, modifier = Modifier.matchParentSize()) {
            if (!onPicture) {
                // Shrinks to fit at large font scales, so it doesn't run into the label.
                Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineLarge,
                        color = textColor,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(maxFontSize = MaterialTheme.typography.headlineLarge.fontSize),
                    )
                }
            }
        }
    }
}

/**
 * A tile's [label], if any, along the bottom edge, and its [content] above it, centred in the whole tile as on a photo
 * (#183). The content gets the height the label leaves and wraps its own; it is centred when that keeps it clear of the
 * label, else it sits just above the label (large font scales on small tiles).
 */
@Composable
private fun CentredOverLabel(label: (@Composable () -> Unit)?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(contents = listOf(content, label ?: {}), modifier = modifier) { (contentMeasurables, labelMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val labelPlaceables = labelMeasurables.map { it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height)) }
        val labelHeight = labelPlaceables.maxOfOrNull { it.height } ?: 0
        val room = (height - labelHeight).coerceAtLeast(0)
        val contentPlaceables = contentMeasurables.map { it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = room)) }
        layout(width, height) {
            contentPlaceables.forEach { it.place(0, ((height - it.height) / 2).coerceAtMost(room - it.height).coerceAtLeast(0)) }
            labelPlaceables.forEach { it.place(0, height - it.height) }
        }
    }
}

/** Fades a photo from clear into [PHOTO_LABEL_SCRIM] above the label, so the scrim has no hard edge. */
private val PHOTO_SCRIM_FADE = Brush.verticalGradient(listOf(Color.Transparent, PHOTO_LABEL_SCRIM))
