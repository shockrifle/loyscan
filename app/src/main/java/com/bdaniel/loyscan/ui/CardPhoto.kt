package com.bdaniel.loyscan.ui

import androidx.compose.ui.graphics.Color
import coil.map.Mapper
import coil.request.Options
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import java.io.File

/** A card's photo (#10) as an `AsyncImage` model. Only the app's loader, `hiltImageLoader()`, knows where its file is. */
data class CardPhoto(val name: String)

/** Maps a [CardPhoto] to its file, registered on the app's `ImageLoader`. */
class CardPhotoMapper(private val photos: FileCardPhotos) : Mapper<CardPhoto, File> {
    override fun map(data: CardPhoto, options: Options): File = photos.file(data.name)
}

/** Behind a label on a card photo (the list tile): white text on it reads even over a white photo, at 5.7:1. */
val PHOTO_LABEL_SCRIM = Color.Black.copy(alpha = 0.6f)

/** How much of a card-shaped tile a store's logo takes (#151): the list's tiles and the logo screen's preview. */
const val TILE_LOGO_FRACTION = 0.6f

/** Width / height of a payment-size card (ISO/IEC 7810 ID-1, 85.60 × 53.98 mm): how a card photo is framed. */
const val CARD_ASPECT_RATIO = 85.60f / 53.98f
