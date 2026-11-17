package com.bdaniel.loyscan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The permissions the app asks for, as Robolectric's package manager reads them from the manifest merged with every
 * library's (the debug unit-test merge): what the Play data-safety declaration rests on.
 */
@RunWith(AndroidJUnit4::class)
class AppPermissionsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val requested: List<String> =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()

    @Test
    fun theApp_doesNotAskForNetworkAccess() { // #157: every image is a private file, nothing is fetched
        assertThat(requested).doesNotContain(Manifest.permission.INTERNET)
    }

    @Test
    fun thePermissionList_comesFromTheMergedManifest() {
        // Guards the test above from passing on a list that was never read: CAMERA is the app's own,
        // ACCESS_NETWORK_STATE comes from a library's manifest (androidx.work, through Glance), so a library that
        // added INTERNET would be seen too.
        assertThat(requested).containsAtLeast(Manifest.permission.CAMERA, Manifest.permission.ACCESS_NETWORK_STATE)
    }
}
