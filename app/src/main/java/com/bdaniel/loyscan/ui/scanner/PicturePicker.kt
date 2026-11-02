package com.bdaniel.loyscan.ui.scanner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/** Opens a picture picker; the scanner reads a barcode from what is picked (#76). A fake in tests. */
@Stable
fun interface PicturePicker {
    fun pick()
}

/**
 * The system photo picker, images only. It needs no storage permission: the app gets a read grant for the one picked
 * picture. Where the photo picker isn't available, androidx falls back to the document picker. [onPicked] gets `null`
 * when the user cancels.
 */
@Composable
fun rememberPicturePicker(onPicked: (Uri?) -> Unit): PicturePicker {
    val launcher = rememberLauncherForActivityResult(PickVisualMedia(), onPicked)
    return remember(launcher) {
        PicturePicker { launcher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    }
}
