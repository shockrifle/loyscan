package com.bdaniel.loyscan.testing

import android.net.Uri
import com.bdaniel.loyscan.data.photos.CardPhotos
import com.bdaniel.loyscan.data.photos.PhotoCapture
import com.bdaniel.loyscan.data.photos.PhotoCrop
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [CardPhotos]: [stored] holds the names of the photos that exist. Imports are named `photo-1`, `photo-2`…,
 * captures `capture-1`, `capture-2`… (stored too: the file exists for the camera app to write).
 */
class FakeCardPhotos(vararg stored: String) : CardPhotos {

    val stored = stored.toMutableSet()

    /** The pictures staged so far, in order; a staged file is named `staged-1`, `staged-2`… and exists. */
    val staged = mutableListOf<Uri>()

    /** When set, [stage] throws it instead of copying, as an unreadable picture does. */
    var importFailure: Exception? = null

    /** When set, [stage] suspends until it completes, as a copy in progress does. */
    var importGate: CompletableDeferred<Unit>? = null

    override suspend fun stage(picture: Uri): String {
        importGate?.await()
        importFailure?.let { throw it }
        staged += picture
        return "staged-${staged.size}".also { stored += it }
    }

    /** The crops made so far, in order: the staged name and the frame it was cropped to. */
    val cropped = mutableListOf<Pair<String, PhotoCrop>>()

    /** When set, [crop] throws it and deletes the staged source, as a picture that can't be encoded does. */
    var cropFailure: Exception? = null

    override suspend fun crop(name: String, crop: PhotoCrop): String {
        cropped += name to crop
        stored -= name // the staged source goes, however the crop ends
        cropFailure?.let { throw it }
        return "photo-${cropped.size}".also { stored += it }
    }

    /** The logos cropped so far, in order: the staged name and the frame. A logo is named `logo-1`, `logo-2`… */
    val croppedLogos = mutableListOf<Pair<String, PhotoCrop>>()

    override suspend fun cropLogo(name: String, crop: PhotoCrop): String {
        croppedLogos += name to crop
        stored -= name // the staged source goes, however the crop ends
        cropFailure?.let { throw it }
        return "logo-${croppedLogos.size}".also { stored += it }
    }

    override suspend fun delete(names: Collection<String>) {
        stored -= names.toSet()
    }

    override fun discard(names: Collection<String>) {
        stored -= names.toSet()
    }

    override suspend fun exists(name: String) = name in stored

    /** The captures made so far, in order. */
    val captures = mutableListOf<PhotoCapture>()

    override fun newCapture(): PhotoCapture {
        val name = "capture-${captures.size + 1}"
        return PhotoCapture(name, Uri.parse("content://photos.test/$name")).also { captures += it; stored += name }
    }
}
