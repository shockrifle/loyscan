package com.bdaniel.loyscan

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
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.bdaniel.loyscan.ui.theme.LoyScanTheme
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalSharedTransitionApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = List,
        modifier = modifier
    ) {
        composable<List> {
            ListScreen(
                animatedVisibilityScope = this,
                directions = navController.listDirections()
            )
        }
        composable<Detail> {
            val detail = it.savedStateHandle.toRoute<Detail>()
            DetailScreen(
                detail.color,
                animatedVisibilityScope = this,
                directions = navController.detailDirections()
            )
        }
        composable<StorePicker> {
            StorePickerScreen(directions = navController.storePickerDirections())
        }
        composable<Scanner> {
            ScannerScreen(directions = navController.scannerDirections(it.toRoute<Scanner>().storeId))
        }
        composable<CardForm> {
            CardFormScreen(directions = navController.cardFormDirections())
        }
    }
}