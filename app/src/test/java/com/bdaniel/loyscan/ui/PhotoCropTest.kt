package com.bdaniel.loyscan.ui

import com.bdaniel.loyscan.data.photos.PhotoCrop
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The crop maths behind the crop screen (#10), pure so it runs without a device (like the barcode layout). */
class PhotoCropTest {

    @Test
    fun aWideFrameOverASquarePicture_keepsTheMiddleStrip() {
        // A 2:1 frame over a square picture, shown to cover the frame (its width fills it): the middle band stays.
        val crop = photoCrop(imageWidth = 100f, imageHeight = 100f, frameWidth = 80f, frameHeight = 40f, scale = 1f, offsetX = 0f, offsetY = 0f)

        assertCrop(crop, left = 0f, top = 0.25f, right = 1f, bottom = 0.75f)
    }

    @Test
    fun aFrameOverAPictureOfTheSameRatio_keepsTheWholePicture() {
        val crop = photoCrop(imageWidth = 200f, imageHeight = 100f, frameWidth = 80f, frameHeight = 40f, scale = 1f, offsetX = 0f, offsetY = 0f)

        assertCrop(crop, left = 0f, top = 0f, right = 1f, bottom = 1f)
    }

    @Test
    fun zoomingIn_keepsASmallerCentredPart() {
        val crop = photoCrop(imageWidth = 100f, imageHeight = 100f, frameWidth = 80f, frameHeight = 40f, scale = 2f, offsetX = 0f, offsetY = 0f)

        assertCrop(crop, left = 0.25f, top = 0.375f, right = 0.75f, bottom = 0.625f)
    }

    @Test
    fun panning_movesThePartKept_clampedToThePicture() {
        // Zoomed to 2×, panned fully to one side: the kept part reaches the picture's edge and no further.
        val crop = photoCrop(imageWidth = 100f, imageHeight = 100f, frameWidth = 80f, frameHeight = 40f, scale = 2f, offsetX = 40f, offsetY = 0f)

        assertCrop(crop, left = 0f, top = 0.375f, right = 0.5f, bottom = 0.625f)
    }

    @Test
    fun clampCropOffset_keepsThePanWithinThePicture() {
        // 2× zoom of a square picture in a 2:1 frame: it can move 40px across and 60px down from centred.
        val wayOut = clampCropOffset(100f, 100f, 80f, 40f, scale = 2f, offsetX = 500f, offsetY = -500f)
        assertThat(wayOut.x).isWithin(1e-4f).of(40f)
        assertThat(wayOut.y).isWithin(1e-4f).of(-60f)

        val within = clampCropOffset(100f, 100f, 80f, 40f, scale = 2f, offsetX = 10f, offsetY = -20f)
        assertThat(within.x).isWithin(1e-4f).of(10f)
        assertThat(within.y).isWithin(1e-4f).of(-20f)
    }

    @Test
    fun cropTransform_zoomsAndPans_keepingThePanWithinThePicture() {
        // Pinch to 2× and pan far right-down on a square picture in a 2:1 frame: scale caps, pan clamps to the edge.
        val t = cropTransform(100f, 100f, 80f, 40f, scale = 1f, offsetX = 0f, offsetY = 0f, panX = 500f, panY = 500f, zoom = 2f)

        assertThat(t.scale).isWithin(1e-4f).of(2f)
        assertThat(t.offset.x).isWithin(1e-4f).of(40f)
        assertThat(t.offset.y).isWithin(1e-4f).of(60f)
    }

    @Test
    fun cropTransform_doesNotZoomOutBelowCoveringTheFrame() {
        val t = cropTransform(100f, 100f, 80f, 40f, scale = 1f, offsetX = 0f, offsetY = 0f, panX = 0f, panY = 0f, zoom = 0.2f)

        assertThat(t.scale).isEqualTo(1f) // never below 1×
    }

    @Test
    fun clampCropOffset_withNoPictureSizeYet_leavesTheOffsetAlone() {
        val offset = clampCropOffset(Float.NaN, Float.NaN, 80f, 40f, scale = 1f, offsetX = 7f, offsetY = 9f)

        assertThat(offset.x).isEqualTo(7f)
        assertThat(offset.y).isEqualTo(9f)
    }

    private fun assertCrop(crop: PhotoCrop, left: Float, top: Float, right: Float, bottom: Float) {
        assertThat(crop.left).isWithin(1e-4f).of(left)
        assertThat(crop.top).isWithin(1e-4f).of(top)
        assertThat(crop.right).isWithin(1e-4f).of(right)
        assertThat(crop.bottom).isWithin(1e-4f).of(bottom)
    }
}
