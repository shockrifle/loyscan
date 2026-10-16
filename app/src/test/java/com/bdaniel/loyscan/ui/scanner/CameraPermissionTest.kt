package com.bdaniel.loyscan.ui.scanner

import android.Manifest
import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.ui.scanner.CameraPermission.Status
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The real permission state, with the system dialog replaced by a registry that answers at once. */
@RunWith(AndroidJUnit4::class)
class CameraPermissionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var permission: CameraPermission

    /** What the "dialog" answers: whether the user allowed the camera; `null` leaves it open. */
    private var userAllows: Boolean? = false
    private val requested = mutableListOf<Any?>()

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            requested += input
            val allows = userAllows ?: return
            if (allows) grant()
            dispatchResult(requestCode, allows)
        }
    }

    private fun grant() = shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

    private fun showRationale(show: Boolean) =
        shadowOf(application.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, show)

    private fun setContent(restoration: StateRestorationTester? = null) {
        if (restoration != null) restoration.setContent { Content() } else composeRule.setContent { Content() }
    }

    @Composable
    private fun Content() {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            },
        ) {
            permission = rememberCameraPermission()
        }
    }

    @Test
    fun beforeAsking_isNotAsked() {
        setContent()

        composeRule.runOnIdle { assertThat(permission.status).isEqualTo(Status.NOT_ASKED) }
    }

    @Test
    fun alreadyAllowed_isGranted() {
        grant()

        setContent()

        composeRule.runOnIdle { assertThat(permission.status).isEqualTo(Status.GRANTED) }
    }

    @Test
    fun request_asksForTheCamera_andAllowingGrantsIt() {
        userAllows = true
        setContent()

        composeRule.runOnIdle { permission.request() }

        composeRule.runOnIdle {
            assertThat(requested).containsExactly(Manifest.permission.CAMERA)
            assertThat(permission.status).isEqualTo(Status.GRANTED)
        }
    }

    @Test
    fun aDenialTheSystemWouldAskAgainFor_isDenied() {
        showRationale(true)
        setContent()

        composeRule.runOnIdle { permission.request() }

        composeRule.runOnIdle { assertThat(permission.status).isEqualTo(Status.DENIED) }
    }

    @Test
    fun aFirstDenialWithoutRationale_canBeAskedAgain() { // Android 11+: closing the dialog unanswered looks like this
        showRationale(false)
        setContent()

        composeRule.runOnIdle { permission.request() }

        composeRule.runOnIdle { assertThat(permission.status).isEqualTo(Status.DENIED) }
    }

    @Test
    fun aRepeatedDenialTheSystemWontAskAgainFor_isDeniedPermanently() {
        showRationale(false)
        setContent()

        composeRule.runOnIdle { permission.request() }
        composeRule.runOnIdle { permission.request() }

        composeRule.runOnIdle { assertThat(permission.status).isEqualTo(Status.DENIED_PERMANENTLY) }
    }

    @Test
    fun aRequestStillOpen_isNotRepeated_evenAfterRecreation() { // e.g. rotating with the dialog up
        userAllows = null
        val restoration = StateRestorationTester(composeRule)
        setContent(restoration)
        composeRule.runOnIdle { permission.request() }

        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { permission.request() }

        composeRule.runOnIdle { assertThat(requested).hasSize(1) }
    }

    @Test
    fun allowingInTheSettings_isSeenOnReturn() {
        setContent()
        composeRule.runOnIdle { permission.request() }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)

        grant()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.runOnIdle { assertThat(permission.status).isEqualTo(Status.GRANTED) }
    }

    @Test
    fun openSettings_opensTheAppsDetailsPage() {
        setContent()

        composeRule.runOnIdle { permission.openSettings() }

        val intent: Intent = shadowOf(application).nextStartedActivity
        assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(intent.data.toString()).isEqualTo("package:${application.packageName}")
    }
}
