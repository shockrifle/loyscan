package com.bdaniel.loyscan.ui.scanner

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The real picker, with the system's photo picker replaced by a registry that answers at once. */
@RunWith(AndroidJUnit4::class)
class PicturePickerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var picker: PicturePicker
    private val requests = mutableListOf<Any?>()
    private val picked = mutableListOf<Uri?>()

    /** What the "picker" answers; `null` is the user cancelling it. */
    private var answer: Uri? = null

    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            requests += input
            dispatchResult(requestCode, answer)
        }
    }

    private fun setContent() = composeRule.setContent {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            },
        ) {
            picker = rememberPicturePicker { picked += it }
        }
    }

    @Test
    fun pick_opensThePhotoPickerForImagesOnly() {
        setContent()

        composeRule.runOnIdle { picker.pick() }

        composeRule.runOnIdle {
            assertThat(requests).hasSize(1)
            assertThat((requests.single() as PickVisualMediaRequest).mediaType).isEqualTo(PickVisualMedia.ImageOnly)
        }
    }

    @Test
    fun aPickedPicture_isHandedOn() {
        answer = Uri.parse("content://media/picker/0/42")
        setContent()

        composeRule.runOnIdle { picker.pick() }

        composeRule.runOnIdle { assertThat(picked).containsExactly(answer) }
    }

    @Test
    fun cancellingThePicker_handsOnNull() {
        setContent()

        composeRule.runOnIdle { picker.pick() }

        composeRule.runOnIdle { assertThat(picked).containsExactly(null) }
    }
}
