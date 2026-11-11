package com.bdaniel.loyscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.bdaniel.loyscan.ui.scanner.PictureScan
import com.bdaniel.loyscan.ui.theme.icons.Icons

/**
 * A picture shared to LoyScan (#77): read first, then its barcode goes on to the store picker. Without one, it says so
 * and offers to enter the number by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPictureScreen(
    directions: SharedPictureDirections,
    viewModel: SharedPictureViewModel = hiltViewModel(),
) {
    val scan by viewModel.state.collectAsState()

    LaunchedEffect(scan) {
        (scan as? PictureScan.Found)?.let { directions.onScanned(it.barcode) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_card)) },
                navigationIcon = {
                    IconButton(onClick = directions.toBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            Modifier.padding(innerPadding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = scan) {
                null, is PictureScan.Found -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reading_picture))
                }
                PictureScan.NoBarcode, PictureScan.Unreadable -> {
                    Text(
                        stringResource(
                            if (current == PictureScan.NoBarcode) {
                                R.string.shared_picture_no_barcode
                            } else {
                                R.string.shared_picture_unreadable
                            }
                        ),
                        textAlign = TextAlign.Center,
                    )
                    FilledTonalButton(onClick = directions.toManualEntry, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.enter_number_by_hand))
                    }
                }
            }
        }
    }
}
