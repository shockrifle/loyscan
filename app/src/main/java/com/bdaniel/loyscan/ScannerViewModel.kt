package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.bdaniel.loyscan.domain.ScannedBarcode
import com.bdaniel.loyscan.ui.scanner.PictureReader
import com.bdaniel.loyscan.ui.scanner.PictureScan
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * State of the scanner screen: torch, the "card won't scan?" hint, reading a picked picture, and the one scan it hands
 * on.
 */
@HiltViewModel
class ScannerViewModel(
    private val hintDelay: Duration,
    private val pictureReader: PictureReader,
) : ViewModel() {

    @Inject
    constructor(pictureReader: PictureReader) : this(HINT_DELAY, pictureReader)

    data class State(
        val torchAvailable: Boolean = false,
        val torchOn: Boolean = false,
        val showHint: Boolean = false,
        val cameraFailed: Boolean = false,
        val cameraUnavailable: Boolean = false,
        val readingPicture: Boolean = false,
        val pictureProblem: PictureProblem? = null,
    )

    /** Why the last picked picture gave no scan. */
    enum class PictureProblem { NO_BARCODE, UNREADABLE }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _pictureScans = Channel<ScannedBarcode>(Channel.BUFFERED)

    /** A barcode read from a picked picture, once, if it is the first scan; the screen hands it on as a camera scan. */
    val pictureScans: Flow<ScannedBarcode> = _pictureScans.receiveAsFlow()

    private var hintTimer: Job? = null
    private var scanned = false

    /** The camera is running. The hint shows [hintDelay] after the first start; a restart doesn't delay it again. */
    fun onCameraStarted(torchAvailable: Boolean) {
        _state.update { it.copy(torchAvailable = torchAvailable, torchOn = it.torchOn && torchAvailable) }
        if (hintTimer == null) {
            hintTimer = viewModelScope.launch {
                delay(hintDelay)
                _state.update { it.copy(showHint = true) }
            }
        }
    }

    /** A camera error after opening, or its end (#40); the camera stays open, so it can come back. */
    fun onCameraAvailable(available: Boolean) = _state.update { it.copy(cameraUnavailable = !available) }

    fun onCameraFailed() = _state.update { it.copy(cameraFailed = true) }

    fun toggleTorch() = _state.update { if (it.torchAvailable) it.copy(torchOn = !it.torchOn) else it }

    /** `true` for the first scan, which the screen hands on; the camera keeps reading, and later scans are dropped. */
    fun onScanned(): Boolean {
        if (scanned) return false
        scanned = true
        return true
    }

    /**
     * Reads a barcode from the picture the user picked (#76); `null` is a cancelled picker, which changes nothing. One
     * picture at a time. Like a camera scan, only the first scan is handed on.
     */
    fun onPicturePicked(uri: Uri?) {
        if (uri == null || _state.value.readingPicture) return
        _state.update { it.copy(readingPicture = true, pictureProblem = null) }
        viewModelScope.launch {
            val scan = pictureReader.read(uri)
            _state.update {
                it.copy(
                    readingPicture = false,
                    pictureProblem = when (scan) {
                        is PictureScan.Found -> null
                        PictureScan.NoBarcode -> PictureProblem.NO_BARCODE
                        PictureScan.Unreadable -> PictureProblem.UNREADABLE
                    },
                )
            }
            if (scan is PictureScan.Found && onScanned()) _pictureScans.send(scan.barcode)
        }
    }

    companion object {
        val HINT_DELAY = 8.seconds
    }
}
