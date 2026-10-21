package com.xlabsx27.naveeoffline.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.xlabsx27.naveeoffline.data.ThemeMode

// Fallback palette (teal) for devices without Material You.
private val Light = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F6),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    secondaryContainer = Color(0xFFCCE8E9),
    tertiary = Color(0xFF4F5F7D),
    tertiaryContainer = Color(0xFFD7E2FF),
    background = Color(0xFFFAFDFC),
    surface = Color(0xFFFAFDFC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF80D4DA),
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F53),
    onPrimaryContainer = Color(0xFF9CF1F6),
    secondary = Color(0xFFB0CCCE),
    secondaryContainer = Color(0xFF324B4D),
    tertiary = Color(0xFFB7C7EA),
    tertiaryContainer = Color(0xFF374764),
    background = Color(0xFF191C1C),
    surface = Color(0xFF191C1C),
)

/** Status colours that read well on both light and dark surfaces. */
object StatusColors {
    val ok = Color(0xFF2E9E5B)
    val warn = Color(0xFFE0A100)
    val bad = Color(0xFFD93B3B)
}

@Composable
fun NaveeTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
