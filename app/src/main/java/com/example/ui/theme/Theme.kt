package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK, AMOLED
}

enum class ReaderThemeMode {
    LIGHT, DARK, SEPIA, AMOLED
}

private val MyBooksLightColorScheme = lightColorScheme(
    primary = BrandPurplePrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleContainerLight,
    onPrimaryContainer = BrandPurpleOnContainerLight,
    secondary = BrandAmber,
    onSecondary = Color.White,
    secondaryContainer = BrandGoldContainerLight,
    onSecondaryContainer = BrandGoldOnContainerLight,
    tertiary = BrandGold,
    onTertiary = BrandGoldOnContainerLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = StatusErrorLight,
    onError = Color.White
)

private val MyBooksDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkGold,
    onSecondary = DarkOnGold,
    secondaryContainer = DarkGoldContainer,
    onSecondaryContainer = DarkOnGoldContainer,
    tertiary = BrandGold,
    onTertiary = DarkOnGold,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = StatusErrorDark,
    onError = Color(0xFF690005)
)

private val MyBooksAmoledColorScheme = MyBooksDarkColorScheme.copy(
    background = AmoledBackground,
    surface = AmoledSurface,
    surfaceVariant = AmoledSurfaceVariant
)

val MyBooksShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyBooksTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    isBangla: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    val colorScheme = when (themeMode) {
        AppThemeMode.AMOLED -> MyBooksAmoledColorScheme
        AppThemeMode.DARK -> MyBooksDarkColorScheme
        AppThemeMode.LIGHT -> MyBooksLightColorScheme
        AppThemeMode.SYSTEM -> if (systemDark) MyBooksDarkColorScheme else MyBooksLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
                ?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildMyBooksTypography(isBangla),
        shapes = MyBooksShapes,
        content = content
    )
}
