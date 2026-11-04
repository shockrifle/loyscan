package com.bdaniel.loyscan

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** The intent a home-screen widget tile fires to open its card (#11), and what [MainActivity] reads back from it. */
@RunWith(AndroidJUnit4::class)
class OpenCardIntentTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun anOpenCardIntent_targetsLoyScan_andCarriesTheCardAndItsColour() {
        val intent = openCardIntent(context, cardId = "card-7", color = "#112233")

        assertThat(intent.component?.className).isEqualTo(MainActivity::class.java.name)
        assertThat(intent.openCard()).isEqualTo(OpenCard("card-7", "#112233"))
    }

    @Test
    fun anIntentWithoutACardId_isNoOpenCard() {
        assertThat(Intent(context, MainActivity::class.java).openCard()).isNull()
    }

    @Test
    fun anOpenCardReopenedFromRecents_isNoOpenCard() { // it was handled when it arrived
        val reopened = openCardIntent(context, "card-7", "#112233")
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertThat(reopened.openCard()).isNull()
    }

    @Test
    fun anOpenCardWithNoColour_readsBackWithAnEmptyColour() { // Detail tolerates an empty/malformed colour
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_CARD_ID, "card-7")

        assertThat(intent.openCard()).isEqualTo(OpenCard("card-7", ""))
    }
}
