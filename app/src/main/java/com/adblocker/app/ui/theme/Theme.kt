package com.adblocker.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Green700,
    onPrimary = Grey50,
    primaryContainer = Green500,
    secondary = Blue500,
    onSecondary = Grey50,
    background = Grey50,
    surface = Grey50,
    onBackground = Grey900,
    onSurface = Grey900
)

private val DarkColorScheme = darkColorScheme(
    primary = Green500,
    onPrimary = Grey900,
    primaryContainer = Green700,
    secondary = Blue500,
    onSecondary = Grey50,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Grey50,
    onSurface = Grey50
)

@Composable
fun AdBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
