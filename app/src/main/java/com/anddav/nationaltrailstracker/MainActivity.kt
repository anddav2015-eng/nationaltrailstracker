package com.anddav.nationaltrailstracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.anddav.nationaltrailstracker.di.LocalAppContainer
import com.anddav.nationaltrailstracker.ui.nav.NationalTrailsNavGraph
import com.anddav.nationaltrailstracker.ui.theme.NationalTrailsTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as NationalTrailsApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                NationalTrailsTrackerTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NationalTrailsNavGraph(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}