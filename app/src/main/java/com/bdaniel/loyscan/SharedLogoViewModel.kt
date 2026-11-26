package com.bdaniel.loyscan

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bdaniel.loyscan.data.photos.CardPhotos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Where copying in a picture shared as a store logo stands (#151): [staged] names the copy once it is made. */
data class SharedLogoState(val staged: String? = null, val failed: Boolean = false)

/**
 * A picture shared to LoyScan as a store logo (#151): copied in at once ([CardPhotos.stage]), while the share's read
 * grant is surely held, before the store is asked for. The copy's name is kept in the [SavedStateHandle], so it isn't
 * made again after process death. A copy abandoned later on is swept with the photo orphans.
 */
@HiltViewModel
class SharedLogoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    photos: CardPhotos,
) : ViewModel() {

    val state: StateFlow<SharedLogoState> =
        combine(savedStateHandle.getStateFlow<String?>(STAGED, null), savedStateHandle.getStateFlow(FAILED, false)) {
                staged, failed ->
            SharedLogoState(staged = staged, failed = failed)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SharedLogoState())

    init {
        if (savedStateHandle.get<String>(STAGED) == null && savedStateHandle.get<Boolean>(FAILED) != true) {
            val uri = Uri.parse(savedStateHandle.toRoute<SharedLogo>().uri)
            viewModelScope.launch {
                try {
                    savedStateHandle[STAGED] = photos.stageLogo(uri) // an SVG is drawn as a PNG (#211)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Couldn't copy the shared picture in")
                    savedStateHandle[FAILED] = true
                }
            }
        }
    }

    private companion object {
        const val STAGED = "staged"
        const val FAILED = "failed"
    }
}
