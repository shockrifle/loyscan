package com.bdaniel.loyscan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import com.bdaniel.loyscan.di.ImageLoaderEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
fun hiltImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context,
            ImageLoaderEntryPoint::class.java
        ).getImageLoader()
    }
}
