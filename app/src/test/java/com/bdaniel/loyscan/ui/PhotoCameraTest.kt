package com.bdaniel.loyscan.ui

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.provider.MediaStore
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bdaniel.loyscan.ui.PhotoCamera.Result
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The real camera glue (#10), with the permission dialog and the camera app replaced by a registry that answers. */
@RunWith(AndroidJUnit4::class)
class PhotoCameraTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var camera: PhotoCamera
    private val results = mutableListOf<Result>()

    /** What was launched: the permission asked for, or the Uri the camera app was given. */
    private val launched = mutableListOf<Any?>()

    /** Whether the user allows the camera in the dialog; `null` leaves it open (see [pendingRequest]). */
    private var userAllows: Boolean? = false

    /** Whether the camera app took a photo. */
    private var photoTaken = true

    /** When set, there is no camera app after all: launching it throws this. */
    private var noCameraApp: ActivityNotFoundException? = null
    private var pendingRequest: Int? = null

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += input
            if (input is Uri) {
                noCameraApp?.let { throw it }
                dispatchResult(requestCode, photoTaken)
                return
            }
            val allows = userAllows ?: return run { pendingRequest = requestCode }
            if (allows) grant()
            dispatchResult(requestCode, allows)
        }
    }

    private fun grant() = shadowOf(application).grantPermissions(Manifest.permission.CAMERA)

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
            camera = rememberPhotoCamera { results += it }
        }
    }

    @Test
    fun withTheCameraAllowed_takeOpensTheCameraApp_onTheTarget_andATakenPhotoIsTaken() {
        grant()
        setContent()

        composeRule.runOnIdle { camera.take(TARGET) }

        composeRule.runOnIdle {
            assertThat(launched).containsExactly(TARGET)
            assertThat(results).containsExactly(Result.TAKEN)
        }
    }

    @Test
    fun aCameraAppThatTookNone_isNotTaken() { // Back in the camera app
        grant()
        photoTaken = false
        setContent()

        composeRule.runOnIdle { camera.take(TARGET) }

        composeRule.runOnIdle { assertThat(results).containsExactly(Result.NOT_TAKEN) }
    }

    @Test
    fun withoutThePermission_takeAsksForItFirst_thenOpensTheCameraApp() {
        userAllows = true
        setContent()

        composeRule.runOnIdle { camera.take(TARGET) }

        composeRule.runOnIdle {
            assertThat(launched).containsExactly(Manifest.permission.CAMERA, TARGET).inOrder()
            assertThat(results).containsExactly(Result.TAKEN)
        }
    }

    @Test
    fun aDenial_isDenied_andARepeatedOneWithoutARationale_isDeniedForGood() {
        setContent()

        composeRule.runOnIdle { camera.take(TARGET) }
        composeRule.runOnIdle { camera.take(TARGET) }

        composeRule.runOnIdle {
            assertThat(launched).containsExactly(Manifest.permission.CAMERA, Manifest.permission.CAMERA)
            assertThat(results).containsExactly(Result.DENIED, Result.DENIED_PERMANENTLY).inOrder()
        }
    }

    @Test
    fun aPermissionAnsweredAfterTheScreenWasRecreated_stillOpensTheCameraApp() { // the dialog is up over a rotation
        userAllows = null
        val restoration = StateRestorationTester(composeRule)
        setContent(restoration)
        composeRule.runOnIdle { camera.take(TARGET) }

        restoration.emulateSavedInstanceStateRestore()
        grant()
        composeRule.runOnIdle { registry.dispatchResult(pendingRequest!!, true) }

        composeRule.runOnIdle {
            assertThat(launched).containsExactly(Manifest.permission.CAMERA, TARGET).inOrder()
            assertThat(results).containsExactly(Result.TAKEN)
        }
    }

    @Test
    fun aCameraAppGoneSinceItWasFound_isNotTaken() { // #10 review: uninstalled or disabled meanwhile
        grant()
        noCameraApp = ActivityNotFoundException("no camera app")
        setContent()

        composeRule.runOnIdle { camera.take(TARGET) }

        composeRule.runOnIdle { assertThat(results).containsExactly(Result.NOT_TAKEN) }
    }

    @Test
    fun withoutACameraApp_isNotAvailable() {
        setContent()

        composeRule.runOnIdle { assertThat(camera.available).isFalse() }
    }

    @Test
    fun withACameraApp_isAvailable() {
        val cameraApp = ComponentName("com.example.camera", "com.example.camera.Capture")
        shadowOf(application.packageManager).apply {
            addActivityIfNotPresent(cameraApp)
            addIntentFilterForActivity(cameraApp, IntentFilter(MediaStore.ACTION_IMAGE_CAPTURE).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        setContent()

        composeRule.runOnIdle { assertThat(camera.available).isTrue() }
    }

    private companion object {
        val TARGET: Uri = Uri.parse("content://com.bdaniel.loyscan.photos/card_photos/1.jpg")
    }
}
