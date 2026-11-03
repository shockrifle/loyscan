package com.bdaniel.loyscan.data.photos

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

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
    fun delete_removesThosePhotos_andSkipsNamesWithoutAFile() = runTest {
        val picked = temporaryFolder.newFile("picked.jpg")
        val kept = photos.import(Uri.fromFile(picked))
        val deleted = photos.import(Uri.fromFile(picked))

        photos.delete(listOf(deleted, "never-stored"))

        assertThat(directory.list()).asList().containsExactly(kept)
    }

    @Test
    fun file_refusesANameThatLeavesThePhotoDirectory() {
        assertThrows(IllegalArgumentException::class.java) { photos.file("../loyscan.db") }
    }
}
