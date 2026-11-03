package com.bdaniel.loyscan.di

import android.content.ContentResolver
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Card photos as the app builds them (#10). */
@RunWith(AndroidJUnit4::class)
class CardPhotosModuleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val photos = CardPhotosModule.provideFileCardPhotos(context)

    @After
    fun tearDown() {
        File(context.filesDir, CardPhotosModule.PHOTO_DIRECTORY).deleteRecursively()
    }

    @Test
    fun theFileProvider_isPrivate_andGrantsOneUriAtATime() {
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.photos", 0)!!

        assertThat(provider.exported).isFalse()
        assertThat(provider.grantUriPermissions).isTrue()
    }

    @Test
    fun aCapture_isSharedThroughTheAppsFileProvider_forTheCameraAppToWriteIntoThePhotoDirectory() {
        // FileProvider matches a file to its root with "/", so on Windows it finds none: this runs on CI (Linux).
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val capture = photos.newCapture()

        context.contentResolver.openOutputStream(capture.uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }

        assertThat(capture.uri.scheme).isEqualTo(ContentResolver.SCHEME_CONTENT)
        assertThat(capture.uri.authority).isEqualTo("${context.packageName}.photos")
        assertThat(context.contentResolver.getType(capture.uri)).isEqualTo("image/jpeg")
        assertThat(photos.file(capture.name).readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }
}
