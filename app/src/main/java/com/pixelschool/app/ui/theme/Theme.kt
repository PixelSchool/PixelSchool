package com.pixelschool.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pixelschool.app.data.local.AppThemePalette

// 1. Violet / Default
private val VioletLight = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

private val VioletDark = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

// 2. Cobalt Sapphire
private val CobaltLight = lightColorScheme(
    primary = Color(0xFF1E56A0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF526070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF0F1D2B),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F)
)

private val CobaltDark = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF00458E),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3B4857),
    onSecondaryContainer = Color(0xFFD6E4F7),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199)
)

// 3. Emerald Green
private val EmeraldLight = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4D6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0B1F16),
    background = Color(0xFFF6FBF6),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF6FBF6),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDDE5DF),
    onSurfaceVariant = Color(0xFF414945),
    outline = Color(0xFF717975)
)

private val EmeraldDark = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFB2DFDB),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE0E3DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFE0E3DF),
    surfaceVariant = Color(0xFF414945),
    onSurfaceVariant = Color(0xFFC1C9C3),
    outline = Color(0xFF8B938E)
)

// 4. Sunset Amber
private val AmberLight = lightColorScheme(
    primary = Color(0xFFB45309),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB8),
    onPrimaryContainer = Color(0xFF2B1700),
    secondary = Color(0xFF715B41),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDDDBE),
    onSecondaryContainer = Color(0xFF281805),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF211A13),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF211A13),
    surfaceVariant = Color(0xFFF0E0D0),
    onSurfaceVariant = Color(0xFF504539),
    outline = Color(0xFF827568)
)

private val AmberDark = darkColorScheme(
    primary = Color(0xFFFFB951),
    onPrimary = Color(0xFF452B00),
    primaryContainer = Color(0xFF633F00),
    onPrimaryContainer = Color(0xFFFFDDB8),
    secondary = Color(0xFFDFC1A2),
    onSecondary = Color(0xFF3F2D17),
    secondaryContainer = Color(0xFF57432B),
    onSecondaryContainer = Color(0xFFFDDDBE),
    background = Color(0xFF19120B),
    onBackground = Color(0xFFEBE0D7),
    surface = Color(0xFF19120B),
    onSurface = Color(0xFFEBE0D7),
    surfaceVariant = Color(0xFF504539),
    onSurfaceVariant = Color(0xFFD4C4B5),
    outline = Color(0xFF9D8E81)
)

// 5. Coral Rose
private val RoseLight = lightColorScheme(
    primary = Color(0xFFBE185D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F001D),
    secondary = Color(0xFF75565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF201A1B),
    surfaceVariant = Color(0xFFF2DDE1),
    onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF847377)
)

private val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5E002B),
    primaryContainer = Color(0xFF83003E),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE5BDC6),
    onSecondary = Color(0xFF432931),
    secondaryContainer = Color(0xFF5B3F48),
    onSecondaryContainer = Color(0xFFFFD9E2),
    background = Color(0xFF181113),
    onBackground = Color(0xFFEFE0E2),
    surface = Color(0xFF181113),
    onSurface = Color(0xFFEFE0E2),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C90)
)

fun getPaletteColorScheme(palette: AppThemePalette, darkTheme: Boolean): ColorScheme {
    return when (palette) {
        AppThemePalette.DYNAMIC, AppThemePalette.VIOLET -> if (darkTheme) VioletDark else VioletLight
        AppThemePalette.COBALT -> if (darkTheme) CobaltDark else CobaltLight
        AppThemePalette.EMERALD -> if (darkTheme) EmeraldDark else EmeraldLight
        AppThemePalette.AMBER -> if (darkTheme) AmberDark else AmberLight
        AppThemePalette.ROSE -> if (darkTheme) RoseDark else RoseLight
    }
}

@Composable
fun PixelSchoolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: AppThemePalette = AppThemePalette.DYNAMIC,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        palette == AppThemePalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getPaletteColorScheme(palette, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = ExpressiveShapes,
        content = content
    )
}
