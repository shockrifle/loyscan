package com.bdaniel.loyscan.data.photos

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch

/** Card photos as files (#10): a picked picture is copied into the photo directory, and deleted from there. */
@RunWith(AndroidJUnit4::class)
class FileCardPhotosTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory by lazy { File(temporaryFolder.root, "card_photos") }
    private val photos by lazy {
        FileCardPhotos(directory, ApplicationProvider.getApplicationContext<Context>().contentResolver)
    }

    @Test
    fun import_copiesThePictureIntoThePhotoDirectory_underANewName() = runTest {
        val picked = temporaryFolder.newFile("picked.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val first = photos.import(Uri.fromFile(picked))
        val second = photos.import(Uri.fromFile(picked))

        assertThat(first).isNotEqualTo(second)
        assertThat(photos.file(first).parentFile).isEqualTo(directory)
        assertThat(photos.file(first).readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(picked.exists()).isTrue()
    }

    @Test
    fun import_ofAPictureThatCantBeRead_throws_andLeavesNoFile() = runTest {
        val missing = Uri.fromFile(File(temporaryFolder.root, "gone.jpg"))

        assertThrows(FileNotFoundException::class.java) { runBlocking { photos.import(missing) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun import_cancelledWhileCopying_leavesNoFile() = runBlocking { // e.g. Back while a cloud picture downloads
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val slow = object : InputStream() {
            private var sent = false

            override fun read(): Int {
                if (sent) return release.await().let { -1 }
                sent = true
                reading.countDown()
                return 1
            }
        }
        val copying = FileCardPhotos(directory, { slow })
        val job = launch(Dispatchers.Default) { copying.import(PICKED) }
        reading.await()

        job.cancel()
        release.countDown()
        job.join()

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun import_ofAPictureThatFailsPartway_throws_andLeavesNoFile() = runTest {
        val failing = object : InputStream() {
            private var sent = false

            override fun read(): Int = if (sent) throw IOException("connection lost") else 1.also { sent = true }
        }

        assertThrows(IOException::class.java) { runBlocking { FileCardPhotos(directory, { failing }).import(PICKED) } }

        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun delete_removesThosePhotos_andSkipsNamesWithoutAFile() = runTest {
        val picked = temporaryFolder.newFile("picked.jpg")
        val kept = photos.import(Uri.fromFile(picked))
        val deleted = photos.import(Uri.fromFile(picked))

        photos.delete(listOf(deleted, "never-stored"))

        assertThat(directory.list()).asList().containsExactly(kept)
    }

    @Test
    fun delete_skipsANameThatIsntAPhotoName_andDeletesTheRest() = runTest { // #87 review: an edit drops such a name
        val picked = temporaryFolder.newFile("picked.jpg")
        val deleted = photos.import(Uri.fromFile(picked))

        photos.delete(listOf("../loyscan.db", deleted))

        assertThat(directory.list()).asList().isEmpty()
        assertThat(picked.exists()).isTrue()
    }

    @Test
    fun discard_deletesThosePhotosInTheBackground() = runTest {
        val background = FileCardPhotos(directory, { null }, StandardTestDispatcher(testScheduler))
        val picked = temporaryFolder.newFile("picked.jpg")
        val kept = photos.import(Uri.fromFile(picked))
        val discarded = photos.import(Uri.fromFile(picked))

        background.discard(listOf(discarded))
        advanceUntilIdle()

        assertThat(directory.list()).asList().containsExactly(kept)
    }

    @Test
    fun exists_saysWhetherThePhotosFileIsThere() = runTest {
        val imported = photos.import(Uri.fromFile(temporaryFolder.newFile("picked.jpg")))

        assertThat(photos.exists(imported)).isTrue()
        assertThat(photos.exists("never-stored")).isFalse()
    }

    @Test
    fun exists_ofANameThatIsntAPhotoName_isFalse() = runTest { // #87 review: the card screen and form ask for every stored name
        assertThat(photos.exists("../loyscan.db")).isFalse()
        assertThat(photos.exists("")).isFalse()
    }

    @Test
    fun deleteUnnamed_deletesOnlyOldPhotosNotNamed() {
        val now = Instant.parse("2026-05-05T05:05:05Z")
        fun photo(name: String, age: Duration) =
            photos.file(name).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)); setLastModified((now - age).toEpochMilli()) }
        val named = photo("named", Duration.ofDays(9))
        val old = photo("old", Duration.ofDays(9))
        val recent = photo("recent", Duration.ofMinutes(1))

        photos.deleteUnnamed(setOf("named"), olderThan = now - Duration.ofDays(1))

        assertThat(named.exists()).isTrue()
        assertThat(old.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
    }

    @Test
    fun file_refusesANameThatLeavesThePhotoDirectory() {
        assertThrows(IllegalArgumentException::class.java) { photos.file("../loyscan.db") }
    }

    private companion object {
        val PICKED: Uri = Uri.parse("content://media/picker/0/1")
    }
}
