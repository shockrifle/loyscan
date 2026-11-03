package com.bdaniel.loyscan

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.IntentCompat
import androidx.navigation.toRoute
import com.bdaniel.loyscan.ui.ScreenOrientation
import com.bdaniel.loyscan.ui.theme.LoyScanTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow

@OptIn(ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Pictures shared to LoyScan (#77), each opened once by [AppNavigation]. */
    private val sharedPictures = Channel<Uri>(Channel.UNLIMITED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process death) has the share's intent again, but its flow is already open.
        if (savedInstanceState == null) intent.sharedPicture()?.let(sharedPictures::trySend)
        addOnNewIntentListener { intent -> intent.sharedPicture()?.let(sharedPictures::trySend) }
        enableEdgeToEdge()
        setContent {
            LoyScanTheme {
                // Screens use transparent Scaffolds so shared elements morph cleanly;
                // the single theme-aware background lives here instead.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SharedTransitionLayout {
                        AppNavigation(sharedPictures = sharedPictures.receiveAsFlow())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AppNavigation(modifier: Modifier = Modifier, sharedPictures: Flow<Uri> = emptyFlow()) {
    val navController = rememberNavController()
    LaunchedEffect(navController) {
        sharedPictures.collect { navController.openSharedPicture(it.toString()) }
    }
    val entry by navController.currentBackStackEntryAsState()
    // Only the card screen turns with a phone, to show the card (and its barcode) wide.
    ScreenOrientation(rotates = entry?.destination?.hasRoute<Detail>())
    NavHost(
        navController = navController,
        startDestination = CardList,
        modifier = modifier
    ) {
        composable<CardList> {
            ListScreen(
                animatedVisibilityScope = this,
                directions = navController.listDirections(it)
            )
        }
        composable<Detail> {
            val detail = it.savedStateHandle.toRoute<Detail>()
            DetailScreen(
                detail.cardId,
                detail.color,
                detail.added,
                animatedVisibilityScope = this,
                directions = navController.detailDirections(it)
            )
        }
        composable<StorePicker> {
            StorePickerScreen(directions = navController.storePickerDirections(it))
        }
        composable<CustomStore> {
            CustomStoreScreen(directions = navController.customStoreDirections(it))
        }
        composable<Scanner> {
            ScannerScreen(directions = navController.scannerDirections(it.toRoute<Scanner>().storeId, it))
        }
        composable<CardForm> {
            CardFormScreen(directions = navController.cardFormDirections(it))
        }
        composable<SharedPicture> {
            SharedPictureScreen(directions = navController.sharedPictureDirections(it))
        }
    }
}

/**
 * The picture shared to LoyScan (#77): one image's `content://` URI, granted with the share. Not a `file://` one, which
 * could name the app's own files, and not a share reopened from recents, which was handled when it arrived.
 */
internal fun Intent.sharedPicture(): Uri? {
    if (action != Intent.ACTION_SEND || type?.startsWith("image/") != true) return null
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    return IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
        ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
}