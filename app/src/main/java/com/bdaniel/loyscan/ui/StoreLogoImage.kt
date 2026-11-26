package com.bdaniel.loyscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bdaniel.loyscan.domain.LogoTreatment

/**
 * A store's [logo] drawn with its [treatment] (#240), through the app's image loader: the My cards tile (and the logo
 * preview, which draws the tile), the card screen's band and the store picker's badge. As is loads the file as it is.
 * TalkBack reads the surface it is on, never the logo itself.
 */
@Composable
fun StoreLogoImage(
    logo: String,
    treatment: LogoTreatment,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val request = remember(context, logo, treatment) {
        ImageRequest.Builder(context)
            .data(CardPhoto(logo))
            .apply { if (treatment != LogoTreatment.AS_IS) transformations(LogoTreatmentTransformation(treatment)) }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        imageLoader = hiltImageLoader(),
        contentScale = contentScale,
        onError = { onError() },
        modifier = modifier,
    )
}
