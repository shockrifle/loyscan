package com.bdaniel.loyscan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.ScannerViewModel.PictureProblem
import com.bdaniel.loyscan.ui.scanner.CameraEvents
import com.bdaniel.loyscan.ui.scanner.CameraPermission
import com.bdaniel.loyscan.ui.scanner.CameraPermission.Status
import com.bdaniel.loyscan.ui.scanner.CameraXScanner
import com.bdaniel.loyscan.ui.scanner.PicturePicker
import com.bdaniel.loyscan.ui.scanner.rememberPicturePicker
import com.bdaniel.loyscan.ui.scanner.ScannerCamera
import com.bdaniel.loyscan.ui.scanner.rememberCameraPermission
import com.bdaniel.loyscan.ui.scanner.Viewfinder
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.ui.theme.icons.Icons

/**
 * Reads a card's barcode with the camera, or from a picked picture, and hands on the first scan. Without the camera
 * permission it explains why it needs it; "Scan from image" and "Enter manually" are always there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    directions: ScannerDirections,
    viewModel: ScannerViewModel = hiltViewModel(),
    permission: CameraPermission = rememberCameraPermission(),
    camera: ScannerCamera = CameraXScanner,
    picker: PicturePicker = rememberPicturePicker(viewModel::onPicturePicked),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (permission.status == Status.NOT_ASKED) permission.request()
    }

    // A picture's scan goes the same way as a camera scan; the ViewModel already let only the first scan through.
    val currentDirections by rememberUpdatedState(directions)
    LaunchedEffect(viewModel) {
        viewModel.pictureScans.collect { currentDirections.onScanned(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_card)) },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (permission.status == Status.GRANTED && state.torchAvailable && !state.cameraUnavailable) {
                        IconToggleButton(checked = state.torchOn, onCheckedChange = { viewModel.toggleTorch() }) {
                            Icon(
                                Icons.Default.Flashlight,
                                contentDescription = stringResource(
                                    if (state.torchOn) R.string.turn_off_torch else R.string.turn_on_torch
                                ),
                            )
                        }
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    permission.status != Status.GRANTED -> PermissionRationale(permission)
                    state.cameraFailed -> Message(stringResource(R.string.camera_failed))
                    else -> {
                        val events = remember(viewModel, directions) {
                            CameraEvents(
                                onStarted = viewModel::onCameraStarted,
                                onFailed = viewModel::onCameraFailed,
                                onAvailable = viewModel::onCameraAvailable,
                                onScanned = { if (viewModel.onScanned()) directions.onScanned(it) },
                            )
                        }
                        camera(state.torchOn, events, Modifier.fillMaxSize().background(Color.Black))
                        if (!state.cameraUnavailable) {
                            // A picture's problem takes the hint's place: one message at a time.
                            Viewfinder(hint = if (state.showHint && state.pictureProblem == null) stringResource(R.string.scan_hint) else null)
                        }
                        // Over the camera, which stays open: CameraX opens it again when the error clears (#40).
                        if (state.cameraUnavailable) {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center,
                            ) {
                                Message(stringResource(R.string.camera_unavailable))
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val pictureProblem = state.pictureProblem
                if (pictureProblem != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                when (pictureProblem) {
                                    PictureProblem.NO_BARCODE -> R.string.scanner_picture_no_barcode
                                    PictureProblem.UNREADABLE -> R.string.scanner_picture_unreadable
                                }
                            ),
                            modifier = Modifier.padding(Spacing.l),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                OutlinedButton(
                    onClick = picker::pick,
                    enabled = !state.readingPicture,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (state.readingPicture) R.string.reading_picture else R.string.scan_from_image))
                }
                FilledTonalButton(onClick = directions.toManualEntry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.enter_manually))
                }
            }
        }
    }
}

@Composable
private fun PermissionRationale(permission: CameraPermission) {
    Column(
        Modifier.padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.camera_needed_for_scan),
            textAlign = TextAlign.Center,
        )
        when (permission.status) {
            Status.DENIED_PERMANENTLY -> {
                Text(
                    stringResource(R.string.camera_turned_off),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = permission::openSettings) { Text(stringResource(R.string.open_settings)) }
            }
            else -> Button(onClick = permission::request) { Text(stringResource(R.string.allow_camera)) }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, modifier = Modifier.padding(Spacing.xl), textAlign = TextAlign.Center)
}
