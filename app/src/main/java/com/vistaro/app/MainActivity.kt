package com.vistaro.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as VistaroApplication).container
        setContent { VistaroNavigation(container) }
    }
}

@Composable
private fun VistaroNavigation(container: AppContainer) {
    val context = LocalContext.current
    val isTv = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
    val currentDensity = LocalDensity.current
    val scaledDensity = remember(currentDensity) {
        Density(
            density = currentDensity.density * 0.8f,
            fontScale = currentDensity.fontScale * 0.8f,
        )
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        TvAppNavigation(container, useBottomNavigation = !isTv)
    }
}
