package com.bdaniel.loyscan.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** "Find a logo online" (#151): a web image search for the store's logo, opened in the browser. */
@RunWith(AndroidJUnit4::class)
class LogoSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun logoSearchUri_isAGoogleImageSearchForTheStoresLogo() {
        assertThat(logoSearchUri("Tesco").toString()).isEqualTo("https://www.google.com/search?tbm=isch&q=Tesco%20logo")
    }

    @Test
    fun logoSearchUri_encodesAccentsSpacesAndAmpersands() {
        assertThat(logoSearchUri("Líra & Co").toString())
            .isEqualTo("https://www.google.com/search?tbm=isch&q=L%C3%ADra%20%26%20Co%20logo")
    }

    @Test
    fun aLogoSearch_opensTheSearchInTheBrowser() {
        val search = rememberedSearch()

        composeRule.runOnIdle { search.open("Tesco") }

        val started = shadowOf(application).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.data).isEqualTo(Uri.parse("https://www.google.com/search?tbm=isch&q=Tesco%20logo"))
    }

    @Test
    fun aLogoSearch_withNoBrowser_saysSo() {
        shadowOf(application).checkActivities(true) // nothing can open the search: startActivity throws, as on a device
        val search = rememberedSearch()

        composeRule.runOnIdle { search.open("Tesco") }

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("No app can open the web search.")
    }

    @Test
    @Config(qualifiers = "+hu")
    fun aLogoSearch_withNoBrowser_saysSoInHungarian() {
        shadowOf(application).checkActivities(true)
        val search = rememberedSearch()

        composeRule.runOnIdle { search.open("Tesco") }

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Nincs olyan alkalmazás, amely megnyitná a webes keresést.")
    }

    private fun rememberedSearch(): LogoSearch {
        lateinit var search: LogoSearch
        composeRule.setContent { search = rememberLogoSearch() }
        return search
    }
}
