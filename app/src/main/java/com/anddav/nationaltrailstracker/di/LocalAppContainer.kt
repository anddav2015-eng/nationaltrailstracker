package com.anddav.nationaltrailstracker.di

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - wrap the app in CompositionLocalProvider(LocalAppContainer provides ...)")
}