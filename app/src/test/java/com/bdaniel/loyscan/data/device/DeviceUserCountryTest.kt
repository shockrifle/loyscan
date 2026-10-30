package com.bdaniel.loyscan.data.device

import android.content.Context
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DeviceUserCountryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val telephony = shadowOf(context.getSystemService(TelephonyManager::class.java))

    @Test
    @Config(qualifiers = "en-rUS")
    fun theSimsCountry_comesFirst() {
        telephony.setSimCountryIso("hu")
        telephony.setNetworkCountryIso("at")

        assertThat(DeviceUserCountry(context).code()).isEqualTo("HU")
    }

    @Test
    @Config(qualifiers = "en-rUS")
    fun withoutASim_theNetworksCountry() {
        telephony.setSimCountryIso("")
        telephony.setNetworkCountryIso("at")

        assertThat(DeviceUserCountry(context).code()).isEqualTo("AT")
    }

    @Test
    @Config(qualifiers = "hu-rHU")
    fun withNeitherSimNorNetwork_theLocalesRegion() {
        telephony.setSimCountryIso("")
        telephony.setNetworkCountryIso("")

        assertThat(DeviceUserCountry(context).code()).isEqualTo("HU")
    }

    @Test
    @Config(qualifiers = "en")
    fun aLocaleWithoutARegion_isNoCountry() {
        telephony.setSimCountryIso("")
        telephony.setNetworkCountryIso("")

        assertThat(DeviceUserCountry(context).code()).isNull()
    }
}
