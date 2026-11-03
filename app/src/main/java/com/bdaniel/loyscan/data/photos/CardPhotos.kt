package com.bdaniel.loyscan.data.photos

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.util.UUID

/**
 * Photos of a user's physical card (#10), kept in app-private storage. A card's row stores a photo's name, not a path,
 * so a restore to another user's data directory still finds it. ViewModels depend on this; tests swap in a fake.
 */
interface CardPhotos {

    /**
     * Copies [picture] (e.g. what the photo picker returned) into the app's storage, and returns the new photo's name.
     * Throws if it can't be read; nothing is left behind then, or when the copy is cancelled.
     */
    suspend fun import(picture: Uri): String

    /** Deletes these photos. A name without a file is skipped. */
    suspend fun delete(names: Collection<String>)

    /** Deletes these photos in the background, for a caller whose own scope is ending (a form being closed). */
    fun discard(names: Collection<String>)

    /** Whether the photo [name] is still there: an old one no card names is swept when the database opens. */
    suspend fun exists(name: String): Boolean
}

/** [CardPhotos] as files in [directory] (`files/card_photos`), never in a shared folder. */
class FileCardPhotos(
    private val directory: File,
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CardPhotos {

    override suspend fun import(picture: Uri): String {
        val name = UUID.randomUUID().toString()
        val file = file(name)
        try {
            withContext(ioDispatcher) {
                directory.mkdirs()
                val input = contentResolver.openInputStream(picture) ?: throw FileNotFoundException("Can't open $picture")
                input.use { from -> file.outputStream().use { from.copyTo(it) } }
            }
            return name
        } catch (e: Throwable) { // cancelled too: a half-copied or unused file mustn't stay
            withContext(NonCancellable + ioDispatcher) { file.delete() }
            throw e
        }
    }

    override suspend fun exists(name: String): Boolean = withContext(ioDispatcher) { file(name).isFile }

    /** Outlives any screen: a discard runs to the end even after its caller is gone. */
    private val background = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun discard(names: Collection<String>) {
        if (names.isNotEmpty()) background.launch { delete(names) }
    }

    override suspend fun delete(names: Collection<String>) = withContext(ioDispatcher) {
        names.forEach { name ->
            val file = file(name)
            if (file.exists() && !file.delete()) Timber.w("Couldn't delete the card photo $name")
        }
    }

    /** Deletes the photos not in [named] last written before [olderThan]. Blocking: for the database's open callback. */
    fun deleteUnnamed(named: Set<String>, olderThan: Instant) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name !in named && it.lastModified() < olderThan.toEpochMilli() }
            .forEach { if (!it.delete()) Timber.w("Couldn't delete the orphaned card photo ${it.name}") }
    }

    /** The file of the photo [name]. A name is a file name only, so it can't reach outside [directory]. */
    fun file(name: String): File {
        require(name.isNotEmpty() && name == File(name).name && name != "..") { "Not a photo name: $name" }
        return File(directory, name)
    }
}
