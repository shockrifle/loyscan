package com.bdaniel.loyscan.data.device

import android.content.Context
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/** The country the user is in, as an upper-case ISO 3166-1 alpha-2 code, or `null` when it isn't known. */
fun interface UserCountry {
    fun code(): String?
}

/**
 * The SIM's country, else the mobile network's, else the region of the device's language: where the user lives
 * rather than which language their phone speaks. Needs no permission (#6).
 */
class DeviceUserCountry @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserCountry {
    override fun code(): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(
            telephony?.simCountryIso,
            telephony?.networkCountryIso,
            context.resources.configuration.locales[0].country,
        ).firstOrNull { it.isNotBlank() }?.uppercase(Locale.ROOT)
    }
}
