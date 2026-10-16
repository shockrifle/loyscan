package com.bdaniel.loyscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** State of the scanner screen: torch, the "card won't scan?" hint, and the one scan it hands on. */
@HiltViewModel
class ScannerViewModel(private val hintDelay: Duration) : ViewModel() {

    @Inject
    constructor() : this(HINT_DELAY)

    data class State(
        val torchAvailable: Boolean = false,
        val torchOn: Boolean = false,
        val showHint: Boolean = false,
        val cameraFailed: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

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

    fun onCameraFailed() = _state.update { it.copy(cameraFailed = true) }

    fun toggleTorch() = _state.update { if (it.torchAvailable) it.copy(torchOn = !it.torchOn) else it }

    /** `true` for the first scan, which the screen hands on; the camera keeps reading, and later scans are dropped. */
    fun onScanned(): Boolean {
        if (scanned) return false
        scanned = true
        return true
    }

    companion object {
        val HINT_DELAY = 8.seconds
    }
}
