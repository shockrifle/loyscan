package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.domain.BarcodeFormat
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.ui.scanner.PictureReader
import com.bdaniel.loyscan.ui.scanner.PictureScan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reads the picture shared to LoyScan (#77) with the scanner's [PictureReader]. [state] is `null` while it is read. What
 * the read found is kept in the [SavedStateHandle], so the picture isn't read again after process death.
 */
@HiltViewModel
class SharedPictureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    pictureReader: PictureReader,
) : ViewModel() {

    private val _state = MutableStateFlow(savedStateHandle.restoredScan())
    val state: StateFlow<PictureScan?> = _state.asStateFlow()

    init {
        if (_state.value == null) {
            val uri = Uri.parse(savedStateHandle.toRoute<SharedPicture>().uri)
            viewModelScope.launch {
                val scan = pictureReader.read(uri)
                savedStateHandle.save(scan)
                _state.value = scan
            }
        }
    }
}

private const val RESULT_KEY = "result"
private const val CONTENT_KEY = "content"
private const val FORMAT_KEY = "format"
private const val GS1_KEY = "gs1"

private fun SavedStateHandle.save(scan: PictureScan) {
    when (scan) {
        is PictureScan.Found -> {
            set(CONTENT_KEY, scan.barcode.content)
            set(FORMAT_KEY, scan.barcode.format.name)
            set(GS1_KEY, scan.barcode.gs1)
            set(RESULT_KEY, "found")
        }
        PictureScan.NoBarcode -> set(RESULT_KEY, "none")
        PictureScan.Unreadable -> set(RESULT_KEY, "unreadable")
    }
}

private fun SavedStateHandle.restoredScan(): PictureScan? = when (get<String>(RESULT_KEY)) {
    "found" -> PictureScan.Found(
        ScannedBarcode(
            content = get<String>(CONTENT_KEY)!!,
            format = BarcodeFormat.valueOf(get<String>(FORMAT_KEY)!!),
            gs1 = get<Boolean>(GS1_KEY) == true,
        ),
    )
    "none" -> PictureScan.NoBarcode
    "unreadable" -> PictureScan.Unreadable
    else -> null
}
