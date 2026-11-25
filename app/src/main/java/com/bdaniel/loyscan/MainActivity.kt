package com.bdaniel.loyscan

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
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
import timber.log.Timber

@OptIn(ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
open class MainActivity : ComponentActivity() {

    /** Pictures shared to LoyScan (#77), each opened once by [AppNavigation]. */
    private val sharedPictures = Channel<Uri>(Channel.UNLIMITED)

    /** Pictures shared to LoyScan as a store logo (#151), each opened once by [AppNavigation]. */
    private val sharedLogos = Channel<Uri>(Channel.UNLIMITED)

    /** Cards the home-screen widget asked to open (#11), each opened once by [AppNavigation]. */
    private val openCards = Channel<OpenCard>(Channel.UNLIMITED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process death) has the launch intent again, but its flow is already open.
        if (savedInstanceState == null) {
            intent.sharedPicture()?.let { picture ->
                if (intent.sharesAsStoreLogo()) sharedLogos.trySend(picture) else sharedPictures.trySend(picture)
            }
            intent.openCard()?.let(openCards::trySend)
        }
        addOnNewIntentListener { intent ->
            // A share reaches this activity again only if its sender asked for SINGLE_TOP. Opening it here would
            // replace what is open, e.g. a half-filled card form (#83), so it goes to a LoyScan of its own.
            if (intent.sharedPicture() != null) intent.forwardToATaskOfItsOwn(this)
            // A widget tap is a deliberate request for one card, so it opens it over the list, in place of whatever
            // is open (#11, #213).
            intent.openCard()?.let(openCards::trySend)
        }
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
                        AppNavigation(
                            sharedPictures = sharedPictures.receiveAsFlow(),
                            sharedLogos = sharedLogos.receiveAsFlow(),
                            openCards = openCards.receiveAsFlow(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * LoyScan started by a share (#77, #151): the same app as [MainActivity], in a task the launcher and the widget never
 * reach (#189).
 */
@AndroidEntryPoint
class ShareActivity : MainActivity()

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AppNavigation(
    modifier: Modifier = Modifier,
    sharedPictures: Flow<Uri> = emptyFlow(),
    sharedLogos: Flow<Uri> = emptyFlow(),
    openCards: Flow<OpenCard> = emptyFlow(),
) {
    val navController = rememberNavController()
    LaunchedEffect(navController) {
        sharedPictures.collect { navController.openSharedPicture(it.toString()) }
    }
    LaunchedEffect(navController) {
        sharedLogos.collect { navController.openSharedLogo(it.toString()) }
    }
    LaunchedEffect(navController) {
        openCards.collect { navController.openCard(it.cardId, it.color) }
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
        composable<StoreLogo> {
            StoreLogoScreen(directions = navController.storeLogoDirections(it))
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
        composable<SharedLogo> {
            SharedLogoScreen(directions = navController.sharedLogoDirections(it))
        }
        composable<About> {
            AboutScreen(directions = navController.aboutDirections(it))
        }
    }
}

/** A card the home-screen widget asked to open (#11): its id and its store colour, for the [Detail] route. */
data class OpenCard(val cardId: String, val color: String)

internal const val EXTRA_OPEN_CARD_ID = "com.bdaniel.loyscan.extra.OPEN_CARD_ID"
internal const val EXTRA_OPEN_CARD_COLOR = "com.bdaniel.loyscan.extra.OPEN_CARD_COLOR"

/**
 * The intent a widget tile fires to open the card [cardId] (its store [color]) in LoyScan (#11). `SINGLE_TOP` so a tap
 * while the app is running reaches the one `MainActivity` through [onCreate]'s `addOnNewIntentListener` instead of
 * stacking a second instance; `MainActivity` is the only activity in its task (shares run in a [ShareActivity] task of
 * their own, #189), so it is always at the top.
 */
internal fun openCardIntent(context: Context, cardId: String, color: String): Intent =
    Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_OPEN_CARD_ID, cardId)
        .putExtra(EXTRA_OPEN_CARD_COLOR, color)

/**
 * The card this intent asks to open (#11), or `null` when it is not a widget tap. Not one reopened from recents, which
 * was handled when it arrived. The colour may be empty; the [Detail] route tolerates an empty or malformed one.
 */
internal fun Intent.openCard(): OpenCard? {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    val cardId = getStringExtra(EXTRA_OPEN_CARD_ID) ?: return null
    return OpenCard(cardId, getStringExtra(EXTRA_OPEN_CARD_COLOR).orEmpty())
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

/** The "Set as store logo" share target (#151): an alias of [ShareActivity] in the manifest. */
internal const val SHARE_AS_STORE_LOGO = "com.bdaniel.loyscan.ShareAsStoreLogo"

/**
 * The share came through "Set as store logo" (#151), not LoyScan's add-card target (#77): the share sheet starts the
 * alias it lists, so the intent names it.
 */
internal fun Intent.sharesAsStoreLogo(): Boolean = component?.className == SHARE_AS_STORE_LOGO

/**
 * Starts the same share in a new LoyScan task, with the picture's read grant passed on. A share LoyScan was granted
 * nothing for can't be passed on: `startActivity` grants an `ACTION_SEND`'s stream itself and throws a
 * `SecurityException`, so that share is dropped and the open screen stays (seen on a device, #83).
 */
internal fun Intent.forwardToATaskOfItsOwn(context: Context, start: (Intent) -> Unit = context::startActivity) {
    // A "Set as store logo" share stays one (#151); any other goes to the share activity (#189).
    val target = component?.takeIf { it.className == SHARE_AS_STORE_LOGO }
        ?: ComponentName(context, ShareActivity::class.java)
    val forwarded = Intent(this)
        .setComponent(target)
        .setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    try {
        start(forwarded)
    } catch (e: SecurityException) {
        Timber.w(e, "A shared picture LoyScan has no grant for was dropped")
    }
}