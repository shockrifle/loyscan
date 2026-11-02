package com.bdaniel.loyscan.testing

import android.net.Uri
import com.bdaniel.loyscan.ui.scanner.PictureReader
import com.bdaniel.loyscan.ui.scanner.PictureScan
import kotlinx.coroutines.CompletableDeferred

/** A [PictureReader] that answers [result] for every picture; [gate], when set, holds a read back until it completes. */
class FakePictureReader(var result: PictureScan = PictureScan.NoBarcode) : PictureReader {
    val reads = mutableListOf<Uri>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun read(uri: Uri): PictureScan {
        reads += uri
        gate?.await()
        return result
    }
}
