package com.mouya.phirachartstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val model = ViewModelProvider(this)[StudioViewModel::class.java]
        setContent {
            val state by model.state.collectAsState()
            val mode = when (state.themeMode) {
                "light" -> ColorSchemeMode.Light
                "dark" -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            val controller = remember(mode) {
                ThemeController(colorSchemeMode = mode, keyColor = Color(0xFF4779E6))
            }
            val dark = when (mode) {
                ColorSchemeMode.Dark -> true
                ColorSchemeMode.Light -> false
                else -> isSystemInDarkTheme()
            }
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            MiuixTheme(controller = controller) {
                StudioScreen(viewModel = model)
            }
        }
    }
}
