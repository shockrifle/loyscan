package com.bdaniel.loyscan.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bdaniel.loyscan.R
import timber.log.Timber

/** Opens a web image search for a store's logo (#151). A screen takes it as a parameter; tests pass a recording one. */
fun interface LogoSearch {
    fun open(storeName: String)
}

/**
 * A Google Images search for "<[storeName]> logo" (#151). The app ships no logos and no links to logo files
 * ([wiki: 2026-11-12-no-bundled-store-logos]): it only opens the search, and the user picks and saves an image themselves.
 */
fun logoSearchUri(storeName: String): Uri = Uri.parse(IMAGE_SEARCH).buildUpon()
    .appendQueryParameter("tbm", "isch")
    .appendQueryParameter("q", "$storeName logo")
    .build()

private const val IMAGE_SEARCH = "https://www.google.com/search"

/** Opens [logoSearchUri] in the browser; with no app to open it, a toast says so instead of crashing. */
@Composable
fun rememberLogoSearch(): LogoSearch {
    val context = LocalContext.current
    // Read in composition, so a language change reaches it (#126).
    val noApp = stringResource(R.string.no_app_for_web_search)
    return remember(context, noApp) {
        LogoSearch { storeName ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, logoSearchUri(storeName)))
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "No app can open the logo search")
                Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
