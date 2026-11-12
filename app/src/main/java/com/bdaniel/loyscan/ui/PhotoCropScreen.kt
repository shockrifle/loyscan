package com.bdaniel.loyscan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.bdaniel.loyscan.R
import com.bdaniel.loyscan.data.photos.MAX_PHOTO_SIDE
import com.bdaniel.loyscan.data.photos.PhotoCrop
import com.bdaniel.loyscan.ui.theme.Spacing
import kotlin.math.max

/** The full-screen crop overlay, so a test can find it. */
const val PHOTO_CROP_TAG = "photoCrop"

/** The card-ratio frame the photo is cropped to. */
const val PHOTO_CROP_FRAME_TAG = "photoCropFrame"

/** The largest the user can zoom a photo in the crop screen. */
private const val MAX_CROP_ZOOM = 8f

/**
 * Frames a card photo to the card's ratio (#10): the staged [photo] shown full screen behind a card-ratio frame the
 * user pans and pinch-zooms it within; the photo shows dimmed around the frame (#108). [onConfirm] stores the framed part ([PhotoCrop.WHOLE] keeps the whole picture,
 * from "Use whole photo"); [onCancel] drops it. Only the app's loader knows where the file is (`hiltImageLoader()`).
 */
@Composable
fun PhotoCropScreen(
    photo: String,
    description: String,
    onConfirm: (PhotoCrop) -> Unit,
    onCancel: () -> Unit,
) {
    // Bounded to MAX_PHOTO_SIDE, not Size.ORIGINAL: a full-sensor camera photo decoded whole (hundreds of MB as ARGB)
    // would OOM. The crop maths uses only the picture's ratio, so a downscaled intrinsic size is fine.
    val request = ImageRequest.Builder(LocalContext.current).data(CardPhoto(photo)).size(MAX_PHOTO_SIDE).build()
    val painter = rememberAsyncImagePainter(model = request, imageLoader = hiltImageLoader())
    var transform by remember { mutableStateOf(CropTransform(1f, Offset.Zero)) }

    BackHandler(onBack = onCancel) // Back cancels the crop, not the whole form
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Consume taps on the scrim, so they don't fall through to the form behind the overlay.
            .pointerInput(Unit) { detectTapGestures {} }
            .testTag(PHOTO_CROP_TAG),
    ) {
        // Inside the system bars: the app draws edge to edge, and Crop sat under the navigation bar (#108).
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.l)) {
            val image = painter.intrinsicSize
            // The frame's size as last laid out, for Crop: a plain holder, read only on a tap.
            val laidOut = remember { FrameHolder() }
            // The stage: the photo, dimmed outside the card-ratio frame in its middle (#108). Clipped, so a zoomed photo
            // stays off the buttons; the gestures work anywhere on it.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                val frame = cropFrameSize(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                laidOut.size = frame
                // The picture covers the frame at scale 1, centred on it, and shows past it; the transform zooms and
                // pans it about the frame's centre, so the crop maths stay relative to the frame.
                val cover = photoCoverSize(image.width, image.height, frame.width, frame.height)
                val density = LocalDensity.current
                Image(
                    painter = painter,
                    contentDescription = description,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .requiredSize(with(density) { DpSize(cover.width.toDp(), cover.height.toDp()) })
                        .graphicsLayer(
                            scaleX = transform.scale,
                            scaleY = transform.scale,
                            translationX = transform.offset.x,
                            translationY = transform.offset.y,
                        ),
                )
                val cutOut = remember { CardCutOutState() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .scrimAround(cutOut)
                        // Keyed on the picture and frame: the gesture lambda captures them, and the picture's size is
                        // unknown until it loads, so this restarts the detector once it is known (else the pan is unclamped).
                        .pointerInput(image, frame) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                transform = cropTransform(image.width, image.height, frame.width, frame.height, transform.scale, transform.offset.x, transform.offset.y, pan.x, pan.y, zoom)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(with(density) { DpSize(frame.width.toDp(), frame.height.toDp()) })
                            .cardCutOut(cutOut)
                            .testTag(PHOTO_CROP_FRAME_TAG)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel), color = Color.White, textAlign = TextAlign.Center)
                }
                TextButton(onClick = { onConfirm(PhotoCrop.WHOLE) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.use_whole_photo), color = Color.White, textAlign = TextAlign.Center)
                }
            }
            Button(
                onClick = {
                    val crop = if (image.hasSize()) {
                        photoCrop(image.width, image.height, laidOut.size.width, laidOut.size.height, transform.scale, transform.offset.x, transform.offset.y)
                    } else {
                        PhotoCrop.WHOLE
                    }
                    onConfirm(crop)
                },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            ) {
                Text(stringResource(R.string.crop_photo))
            }
        }
    }
}

/** Holds the crop frame's laid-out size outside snapshot state: written while laying out, read on a tap. */
private class FrameHolder(var size: Size = Size.Zero)

/** Whether a Coil painter has reported its picture's size yet. */
private fun Size.hasSize() = isSpecified && width > 0f && height > 0f

/** The scale and offset after a pan-and-zoom gesture, zoom coerced to [1, [MAX_CROP_ZOOM]] and the pan kept in the picture. */
data class CropTransform(val scale: Float, val offset: Offset)

/** Applies a pan ([panX], [panY]) and pinch [zoom] to the crop screen's current [scale]/[offsetX]/[offsetY]. Pure, so it is tested. */
fun cropTransform(
    imageWidth: Float,
    imageHeight: Float,
    frameWidth: Float,
    frameHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    panX: Float,
    panY: Float,
    zoom: Float,
): CropTransform {
    val newScale = (scale * zoom).coerceIn(1f, MAX_CROP_ZOOM)
    val offset = clampCropOffset(imageWidth, imageHeight, frameWidth, frameHeight, newScale, offsetX + panX, offsetY + panY)
    return CropTransform(newScale, offset)
}

/**
 * The part of an [imageWidth]×[imageHeight] picture under a [frameWidth]×[frameHeight] crop frame, as a [PhotoCrop],
 * when the picture is shown to cover the frame (its shorter side fills it, [ContentScale.Crop]), then zoomed by [scale]
 * (≥ 1) and panned by ([offsetX], [offsetY]) px from centred. Pure, so the crop maths is tested without a device.
 */
fun photoCrop(
    imageWidth: Float,
    imageHeight: Float,
    frameWidth: Float,
    frameHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): PhotoCrop {
    val base = max(frameWidth / imageWidth, frameHeight / imageHeight)
    val shownWidth = imageWidth * base * scale
    val shownHeight = imageHeight * base * scale
    // The picture's top-left in the frame's coordinates (frame origin top-left), centred then panned.
    val left = (frameWidth - shownWidth) / 2f + offsetX
    val top = (frameHeight - shownHeight) / 2f + offsetY
    return PhotoCrop(
        left = ((0f - left) / shownWidth).coerceIn(0f, 1f),
        top = ((0f - top) / shownHeight).coerceIn(0f, 1f),
        right = ((frameWidth - left) / shownWidth).coerceIn(0f, 1f),
        bottom = ((frameHeight - top) / shownHeight).coerceIn(0f, 1f),
    )
}

/**
 * ([offsetX], [offsetY]) coerced so an [imageWidth]×[imageHeight] picture, shown to cover a [frameWidth]×[frameHeight]
 * frame and zoomed by [scale], can't be panned past where its edge meets the frame. The offset is returned unchanged
 * while the picture's size isn't known yet. Pure, so the pan limits are tested without a device.
 */
fun clampCropOffset(
    imageWidth: Float,
    imageHeight: Float,
    frameWidth: Float,
    frameHeight: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): Offset {
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) {
        return Offset(offsetX, offsetY)
    }
    val base = max(frameWidth / imageWidth, frameHeight / imageHeight)
    val overflowX = (imageWidth * base * scale - frameWidth) / 2f
    val overflowY = (imageHeight * base * scale - frameHeight) / 2f
    return Offset(offsetX.coerceIn(-overflowX, overflowX), offsetY.coerceIn(-overflowY, overflowY))
}


/** The crop frame in a [stageWidth]×[stageHeight] stage: the card's ratio, as wide as the stage, or as tall when it is short. */
fun cropFrameSize(stageWidth: Float, stageHeight: Float): Size =
    if (stageWidth / CARD_ASPECT_RATIO <= stageHeight) Size(stageWidth, stageWidth / CARD_ASPECT_RATIO)
    else Size(stageHeight * CARD_ASPECT_RATIO, stageHeight)

/**
 * The size an [imageWidth]×[imageHeight] picture is shown at to cover a [frameWidth]×[frameHeight] frame (its shorter side
 * fills it), before zooming; the frame itself while the picture's size isn't known (still loading, or failed).
 */
fun photoCoverSize(imageWidth: Float, imageHeight: Float, frameWidth: Float, frameHeight: Float): Size {
    if (!imageWidth.isFinite() || !imageHeight.isFinite() || imageWidth <= 0f || imageHeight <= 0f) {
        return Size(frameWidth, frameHeight)
    }
    val base = max(frameWidth / imageWidth, frameHeight / imageHeight)
    return Size(imageWidth * base, imageHeight * base)
}
