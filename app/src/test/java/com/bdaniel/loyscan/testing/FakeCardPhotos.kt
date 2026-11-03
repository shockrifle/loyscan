package com.bdaniel.loyscan.testing

import android.net.Uri
import com.bdaniel.loyscan.data.photos.CardPhotos
import kotlinx.coroutines.CompletableDeferred

/** In-memory [CardPhotos]: [stored] holds the names of the photos that exist. Imports are named `photo-1`, `photo-2`… */
class FakeCardPhotos(vararg stored: String) : CardPhotos {

    val stored = stored.toMutableSet()

    /** The pictures imported so far, in order. */
    val imported = mutableListOf<Uri>()

    /** When set, [import] throws it instead of copying, as an unreadable picture does. */
    var importFailure: Exception? = null

    /** When set, [import] suspends until it completes, as a copy in progress does. */
    var importGate: CompletableDeferred<Unit>? = null

    override suspend fun import(picture: Uri): String {
        importGate?.await()
        importFailure?.let { throw it }
        imported += picture
        return "photo-${imported.size}".also { stored += it }
    }

    override suspend fun delete(names: Collection<String>) {
        stored -= names.toSet()
    }

    override fun discard(names: Collection<String>) {
        stored -= names.toSet()
    }
}
