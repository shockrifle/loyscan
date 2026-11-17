package com.bdaniel.loyscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bdaniel.loyscan.ui.theme.Spacing
import com.bdaniel.loyscan.ui.theme.icons.Icons

/**
 * The app's name and [version], and that LoyScan isn't affiliated with or endorsed by the retailers whose cards it
 * stores (#150; why: `wiki/decisions/2026-11-12-no-bundled-store-logos.md`). Static, so it has no ViewModel. The page
 * scrolls, so a large font on a small phone still reaches the whole note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(directions: AboutDirections, version: String = BuildConfig.VERSION_NAME) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
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
            Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.about_not_affiliated),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
    }
}
