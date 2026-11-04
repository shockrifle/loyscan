package com.bdaniel.loyscan.data.photos

import android.content.ContentResolver
import android.net.Uri
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/**
 * Photos of a user's physical card (#10), kept in app-private storage. A card's row stores a photo's name, not a path,
 * so a restore to another user's data directory still finds it. ViewModels depend on this; tests swap in a fake.
 */
interface CardPhotos {

    /**
     * Stores a re-encoded copy of [picture] (e.g. what the photo picker returned) in the app's storage: downscaled,
     * upright and without its metadata (#88). Returns the new photo's name. Throws if it can't be read or isn't a
     * picture that can be decoded; nothing is left behind then, or when the import is cancelled.
     */
    suspend fun import(picture: Uri): String

    /** Deletes these photos. A name without a file, or one that isn't a photo's name, is skipped. */
    suspend fun delete(names: Collection<String>)

    /** Deletes these photos in the background, so the caller needn't wait: a photo replaced or removed, or a capture the camera app never answered. */
    fun discard(names: Collection<String>)

    /** Whether the photo [name] is still there: an old one no card names is swept when the database opens. */
    suspend fun exists(name: String): Boolean

    /**
     * A new file for the camera app to write a photo into (#10), to [import] like a pick. It is deleted by its name
     * once imported, or when the camera app took none; one a killed process leaves is swept with the orphans.
     */
    fun newCapture(): PhotoCapture
}

/** A file the camera app writes a new photo into: [uri] is what the camera app is given, [name] deletes it. */
data class PhotoCapture(val name: String, val uri: Uri)

/**
 * [card] without the photos whose files are missing, e.g. after a cloud restore, which brings back the database but not
 * the photos (#87). Shown as no photo; a saved edit then clears the name.
 */
suspend fun CardPhotos.withoutMissing(card: LoyaltyCard): LoyaltyCard = card.copy(
    frontPhotoPath = card.frontPhotoPath?.takeIf { exists(it) },
    backPhotoPath = card.backPhotoPath?.takeIf { exists(it) },
)

/**
 * [CardPhotos] as files in [directory] (`files/card_photos`), never in a shared folder. [openPicture] opens a picked
 * picture; tests pass a stream that blocks or fails partway. [shareCapture] gives the camera app a capture's file: the
 * app's `FileProvider`, a plain file Uri in tests.
 */
class FileCardPhotos(
    private val directory: File,
    private val openPicture: (Uri) -> InputStream?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val shareCapture: (File) -> Uri = Uri::fromFile,
) : CardPhotos {

    constructor(directory: File, contentResolver: ContentResolver, shareCapture: (File) -> Uri = Uri::fromFile) :
        this(directory, contentResolver::openInputStream, shareCapture = shareCapture)

    /**
     * Copies the picture in as it is, then stores it re-encoded ([encodeCardPhoto]): smaller, the right way up and
     * without its metadata (#88). The copy is read locally, so a cloud picture is downloaded once; it is deleted after,
     * and one a killed process leaves behind is swept with the orphans.
     */
    override suspend fun import(picture: Uri): String {
        val name = UUID.randomUUID().toString()
        val file = file(name)
        val original = File(directory, "$name.original")
        try {
            withContext(ioDispatcher) {
                directory.mkdirs()
                val input = openPicture(picture) ?: throw FileNotFoundException("Can't open $picture")
                input.use { from -> original.outputStream().use { from.copyTo(it) } }
                ensureActive()
                encodeCardPhoto(original, file)
            }
            return name
        } catch (e: Throwable) { // cancelled too: a half-written or unused file mustn't stay
            withContext(NonCancellable + ioDispatcher) { file.delete() }
            throw e
        } finally {
            withContext(NonCancellable + ioDispatcher) { original.delete() }
        }
    }

    /**
     * Next to the photos, so the sweep catches one a killed process leaves. A `.jpg`, so the camera app is offered an
     * `image/jpeg` target. Creates the directory on the caller's thread: the camera app's write needs it, and the Uri
     * is needed at once to start the camera.
     */
    override fun newCapture(): PhotoCapture {
        val name = "${UUID.randomUUID()}.jpg"
        directory.mkdirs()
        return PhotoCapture(name, shareCapture(file(name)))
    }

    /** A name that isn't a photo's has no file, rather than throwing: it comes from a row, which a screen shows (#87). */
    override suspend fun exists(name: String): Boolean =
        isPhotoName(name) && withContext(ioDispatcher) { file(name).isFile }

    /** Outlives any screen: a discard runs to the end even after its caller is gone. */
    private val background = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun discard(names: Collection<String>) {
        if (names.isNotEmpty()) background.launch { delete(names) }
    }

    override suspend fun delete(names: Collection<String>) = withContext(ioDispatcher) {
        names.filter(::isPhotoName).forEach { name ->
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
        require(isPhotoName(name)) { "Not a photo name: $name" }
        return File(directory, name)
    }

    private fun isPhotoName(name: String) = name.isNotEmpty() && name == File(name).name && name != ".."
}
