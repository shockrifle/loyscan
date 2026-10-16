package com.bdaniel.loyscan.ui.scanner

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Scanning must work on phones without Google Play Services (#5): no Play Services or ML Kit library may reach the
 * app, also not as a transitive dependency of CameraX or zxing-cpp. The unit-test classpath holds the debug
 * runtime dependencies, so a class from them would load here.
 */
class NoPlayServicesTest {

    @Test
    fun noPlayServicesOrMlKitClass_isOnTheClasspath() {
        val forbidden = listOf(
            "com.google.android.gms.common.GoogleApiAvailability",
            "com.google.android.gms.tasks.Task",
            "com.google.mlkit.vision.barcode.BarcodeScanning",
            "com.google.mlkit.common.MlKit",
            "androidx.camera.mlkit.vision.MlKitAnalyzer",
        )

        val found = forbidden.filter { runCatching { Class.forName(it, false, javaClass.classLoader) }.isSuccess }

        assertWithMessage("Play Services / ML Kit classes on the classpath").that(found).isEmpty()
    }
}
