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
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/**
 * Photos of a user's physical card (#10), kept in app-private storage. A card's row stores a photo's name, not a path,
 * so a restore to another user's data directory still finds it. ViewModels depend on this; tests swap in a fake.
 */
interface CardPhotos {

    /**
     * Copies [picture] (e.g. what the photo picker returned) into the app's storage as it is, for the crop screen to
     * show and [crop] to store (#10). Returns the staged file's name, a valid photo name so the app's loader can show
     * it; it survives process death, since the picker's read grant ends with the process. A captured photo is written
     * straight into a [newCapture] file, which is a staged file already, so it isn't staged again. Throws if [picture]
     * can't be read or isn't a picture that can be decoded; nothing is left behind then, or when the copy is cancelled.
     */
    suspend fun stage(picture: Uri): String

    /**
     * As [stage], for a store's logo (#211): a picture is copied in as it is, and an SVG, which [stage] refuses, is drawn
     * once onto a transparent PNG at [MAX_LOGO_SIDE] pixels on its long side ([drawSvgLogo]), so the crop and [cropLogo]
     * only ever see a PNG. Throws if [picture] can't be read, or is neither a picture nor an SVG that can be drawn.
     */
    suspend fun stageLogo(picture: Uri): String

    /**
     * Copies the stored photo [name] (a card's front photo) to a new staged file, as [stage] copies a pick, for the crop
     * screen to frame it as a store's logo (#151): [cropLogo] deletes its source, so the card keeps its own photo.
     * Throws if [name] isn't a photo's name, its file is missing or it isn't a picture; nothing is left behind then.
     */
    suspend fun stagePhoto(name: String): String

    /**
     * Stores the staged photo [name] (from [stage] or a [newCapture] the camera app answered) re-encoded and cropped to
     * [crop] (#10, #88): a JPEG, downscaled, upright and without its metadata. Returns the new photo's name and deletes
     * the staged file. Throws if it can't be decoded or encoded; nothing is left behind then, the staged file included.
     */
    suspend fun crop(name: String, crop: PhotoCrop): String

    /**
     * Stores the staged picture [name] as a store's logo (#151), cropped to [crop]: a PNG that keeps its transparency,
     * at most [MAX_LOGO_SIDE] pixels on its long side, upright and without its metadata. [crop] may reach past the
     * picture (a logo framed zoomed out); that part is transparent. Returns the logo's name, in the photos' directory,
     * and deletes the staged file. Throws if it can't be decoded or encoded; nothing is left behind then.
     */
    suspend fun cropLogo(name: String, crop: PhotoCrop): String

    /** Deletes these photos. A name without a file, or one that isn't a photo's name, is skipped. */
    suspend fun delete(names: Collection<String>)

    /** Deletes these photos in the background, so the caller needn't wait: a photo replaced or removed, or a capture the camera app never answered. */
    fun discard(names: Collection<String>)

    /** Whether the photo [name] is still there: an old one no card names is swept when the database opens. */
    suspend fun exists(name: String): Boolean

    /**
     * A new file for the camera app to write a photo into (#10): a staged file already, so the crop screen opens on it
     * and [crop] stores it like a pick. It is deleted when the camera app took none, or by [crop] once framed; one a
     * killed process leaves is swept with the orphans.
     */
    fun newCapture(): PhotoCapture
}

/** A file the camera app writes a new photo into: [uri] is what the camera app is given, [name] deletes it. */
data class PhotoCapture(val name: String, val uri: Uri)

/**
 * [card] without the photos whose files are missing, e.g. a file deleted or otherwise lost while the row still names it
 * (#87). Shown as no photo; a saved edit then clears the name. Its store's logo goes the same way when its file is
 * missing (#151): the name on the brand colour shows instead.
 */
suspend fun CardPhotos.withoutMissing(card: LoyaltyCard): LoyaltyCard = withoutMissing(listOf(card)).single()

/** [cards] each [withoutMissing]; a store's logo is checked once, however many of its cards there are (#151). */
suspend fun CardPhotos.withoutMissing(cards: List<LoyaltyCard>): List<LoyaltyCard> {
    val logos = cards.mapNotNullTo(mutableSetOf()) { it.store.logo }.filterTo(mutableSetOf()) { exists(it) }
    return cards.map { card ->
        card.copy(
            frontPhotoPath = card.frontPhotoPath?.takeIf { exists(it) },
            backPhotoPath = card.backPhotoPath?.takeIf { exists(it) },
            store = card.store.copy(logo = card.store.logo?.takeIf { it in logos }),
        )
    }
}

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
    private val drawSvg: (source: File, target: File) -> Unit = ::drawSvgLogo,
) : CardPhotos {

    constructor(directory: File, contentResolver: ContentResolver, shareCapture: (File) -> Uri = Uri::fromFile) :
        this(directory, contentResolver::openInputStream, shareCapture = shareCapture)

    /**
     * Copies the picture in as it is (#10). The copy is read locally, so a cloud picture is downloaded once; a half-
     * written one left by a failure, a cancel or a killed process is deleted, or swept with the orphans. A file that
     * isn't a picture is refused here, so the crop screen only ever shows one that can be shown ([encodeCardPhoto]).
     */
    override suspend fun stage(picture: Uri): String =
        copyIn(picture, open = { openPicture(picture) ?: throw FileNotFoundException("Can't open $picture") })

    /**
     * As [stage] (#211), but a copy that isn't a picture is drawn as an SVG ([drawSvg]) on the IO dispatcher, and the
     * drawing replaces it under the same name, so a failure or a cancel leaves nothing behind as for [stage].
     */
    override suspend fun stageLogo(picture: Uri): String =
        copyIn(picture, open = { openPicture(picture) ?: throw FileNotFoundException("Can't open $picture") }) { copy ->
            if (!isPicture(copy)) {
                val drawn = file(UUID.randomUUID().toString())
                try {
                    drawSvg(copy, drawn)
                    drawn.copyTo(copy, overwrite = true)
                } finally {
                    drawn.delete()
                }
            }
        }

    /** As [stage] copies a pick (#151); [file] refuses a name that isn't a photo's, and a missing one can't be opened. */
    override suspend fun stagePhoto(name: String): String {
        val photo = file(name)
        return copyIn(name, open = { photo.inputStream() })
    }

    /**
     * Copies what [open] reads to a new staged file and hands it to [finish], on the IO dispatcher: by default it checks
     * the copy is a picture; [source] names it in a failure.
     */
    private suspend fun copyIn(
        source: Any,
        open: () -> InputStream,
        finish: (copy: File) -> Unit = { if (!isPicture(it)) throw IOException("Not a picture: $source") },
    ): String {
        val name = UUID.randomUUID().toString()
        val file = file(name)
        try {
            withContext(ioDispatcher) {
                directory.mkdirs()
                open().use { from -> file.outputStream().use { from.copyTo(it) } }
                ensureActive()
                finish(file)
            }
            return name
        } catch (e: Throwable) { // cancelled too: a half-written or unused file mustn't stay
            withContext(NonCancellable + ioDispatcher) { file.delete() }
            throw e
        }
    }

    /**
     * Stores the staged photo re-encoded and cropped ([encodeCardPhoto]): smaller, the right way up, without its
     * metadata (#88) and framed to the card (#10). The staged file goes once the photo is written; a half-written final
     * left by a failure is deleted, and the staged file with it.
     */
    override suspend fun crop(name: String, crop: PhotoCrop): String = encodeStaged(name) { staged, file ->
        encodeCardPhoto(staged, file, crop)
    }

    /** Encodes the staged file [name] into a new file with [encode]; a half-written new file and the staged one are deleted. */
    private suspend fun encodeStaged(name: String, encode: (staged: File, target: File) -> Unit): String {
        val staged = file(name)
        val cropped = UUID.randomUUID().toString()
        val file = file(cropped)
        try {
            withContext(ioDispatcher) { encode(staged, file) }
            return cropped
        } catch (e: Throwable) {
            withContext(NonCancellable + ioDispatcher) { file.delete() }
            throw e
        } finally {
            withContext(NonCancellable + ioDispatcher) { staged.delete() }
        }
    }

    /** Stores the staged picture as a logo ([encodeStoreLogo]), as [crop] stores a photo: the staged file goes either way. */
    override suspend fun cropLogo(name: String, crop: PhotoCrop): String = encodeStaged(name) { staged, file ->
        encodeStoreLogo(staged, file, crop)
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
