package com.bdaniel.loyscan.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.request.Options
import com.bdaniel.loyscan.data.photos.FileCardPhotos
import com.bdaniel.loyscan.di.ImageLoaderModule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** A card photo (#10) is loaded from its file by the app's image loader, the one `hiltImageLoader()` returns. */
@RunWith(AndroidJUnit4::class)
class CardPhotoTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun theAppsImageLoader_mapsACardPhotoToItsFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photos = FileCardPhotos(temporaryFolder.newFolder("card_photos"), context.contentResolver)
        val loader = ImageLoaderModule.provideImageLoader(context, photos)

        val mapped = loader.components.map(CardPhoto("front"), Options(context))

        assertThat(mapped).isEqualTo(photos.file("front"))
    }
}
