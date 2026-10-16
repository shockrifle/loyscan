package com.bdaniel.loyscan.testing

import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy

/** A camera frame with no pixels, for code that only passes frames on and closes them. */
class FakeImageProxy : ImageProxy {
    var closed = false
        private set

    override fun close() {
        closed = true
    }

    override fun getCropRect(): Rect = error("not used")
    override fun setCropRect(rect: Rect?) = error("not used")
    override fun getFormat(): Int = error("not used")
    override fun getHeight(): Int = error("not used")
    override fun getWidth(): Int = error("not used")
    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = error("not used")
    override fun getImageInfo(): ImageInfo = error("not used")
    override fun getImage(): Image? = error("not used")
}
