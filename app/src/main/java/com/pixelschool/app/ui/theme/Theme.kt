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
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceDim = Color(0xFFDED8E1),
    surfaceBright = Color(0xFFFEF7FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = Color(0xFF322F35),
    inverseOnSurface = Color(0xFFF5EFF7),
    inversePrimary = PrimaryDark
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
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceDim = Color(0xFF141218),
    surfaceBright = Color(0xFF3B383E),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
    inversePrimary = PrimaryLight
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
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3DAFF),
    onTertiaryContainer = Color(0xFF251432),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFF8F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFEFF0F7),
    inversePrimary = Color(0xFFA8C8FF)
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
    tertiary = Color(0xFFD7BDE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF3DAFF),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceDim = Color(0xFF111418),
    surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0C0F13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF1E56A0)
)

// 3. Emerald Green
private val EmeraldLight = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF73F8E7),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF05201B),
    tertiary = Color(0xFF446279),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9E6FF),
    onTertiaryContainer = Color(0xFF001E2F),
    background = Color(0xFFF4FAF7),
    onBackground = Color(0xFF161D1B),
    surface = Color(0xFFF4FAF7),
    onSurface = Color(0xFF161D1B),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceDim = Color(0xFFD5DBD8),
    surfaceBright = Color(0xFFF4FAF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE3EAE6),
    surfaceContainerHighest = Color(0xFFDDE4E0),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
    inverseSurface = Color(0xFF2B322F),
    inverseOnSurface = Color(0xFFECF2EF),
    inversePrimary = Color(0xFF52DBCB)
)

private val EmeraldDark = darkColorScheme(
    primary = Color(0xFF52DBCB),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF73F8E7),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFACCAE5),
    onTertiary = Color(0xFF133448),
    tertiaryContainer = Color(0xFF2C4B60),
    onTertiaryContainer = Color(0xFFC9E6FF),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    surfaceDim = Color(0xFF0E1513),
    surfaceBright = Color(0xFF343B39),
    surfaceContainerLowest = Color(0xFF09100E),
    surfaceContainerLow = Color(0xFF161D1B),
    surfaceContainer = Color(0xFF1A211F),
    surfaceContainerHigh = Color(0xFF252C2A),
    surfaceContainerHighest = Color(0xFF303634),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
    inverseSurface = Color(0xFFDEE4E1),
    inverseOnSurface = Color(0xFF2B322F),
    inversePrimary = Color(0xFF006A60)
)

// 4. Sunset Amber
private val AmberLight = lightColorScheme(
    primary = Color(0xFF895200),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB6),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF715B40),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDDEC0),
    onSecondaryContainer = Color(0xFF281804),
    tertiary = Color(0xFF54643E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD7EABF),
    onTertiaryContainer = Color(0xFF121F03),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF211A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFEFE0CF),
    onSurfaceVariant = Color(0xFF4F4539),
    surfaceDim = Color(0xFFE5DDD5),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E6),
    surfaceContainer = Color(0xFFF9EBE0),
    surfaceContainerHigh = Color(0xFFF3E5DA),
    surfaceContainerHighest = Color(0xFFEDE0D5),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD2C4B4),
    inverseSurface = Color(0xFF372F28),
    inverseOnSurface = Color(0xFFFCEDE3),
    inversePrimary = Color(0xFFFFB868)
)

private val AmberDark = darkColorScheme(
    primary = Color(0xFFFFB868),
    onPrimary = Color(0xFF492900),
    primaryContainer = Color(0xFF683D00),
    onPrimaryContainer = Color(0xFFFFDDB6),
    secondary = Color(0xFFDFC2A2),
    onSecondary = Color(0xFF3E2D17),
    secondaryContainer = Color(0xFF57432B),
    onSecondaryContainer = Color(0xFFFDDEC0),
    tertiary = Color(0xFFBCCEA4),
    onTertiary = Color(0xFF273514),
    tertiaryContainer = Color(0xFF3D4C28),
    onTertiaryContainer = Color(0xFFD7EABF),
    background = Color(0xFF18120C),
    onBackground = Color(0xFFECE0D7),
    surface = Color(0xFF18120C),
    onSurface = Color(0xFFECE0D7),
    surfaceVariant = Color(0xFF4F4539),
    onSurfaceVariant = Color(0xFFD2C4B4),
    surfaceDim = Color(0xFF18120C),
    surfaceBright = Color(0xFF3F3831),
    surfaceContainerLowest = Color(0xFF120D08),
    surfaceContainerLow = Color(0xFF211A14),
    surfaceContainer = Color(0xFF251E18),
    surfaceContainerHigh = Color(0xFF302922),
    surfaceContainerHighest = Color(0xFF3B332C),
    outline = Color(0xFF9C8F80),
    outlineVariant = Color(0xFF4F4539),
    inverseSurface = Color(0xFFECE0D7),
    inverseOnSurface = Color(0xFF372F28),
    inversePrimary = Color(0xFF895200)
)

// 5. Coral Rose
private val RoseLight = lightColorScheme(
    primary = Color(0xFF9F2A56),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3E001D),
    secondary = Color(0xFF75565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E2),
    onSecondaryContainer = Color(0xFF2B151C),
    tertiary = Color(0xFF7D5634),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF211A1C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF211A1C),
    surfaceVariant = Color(0xFFF2DDE1),
    onSurfaceVariant = Color(0xFF514347),
    surfaceDim = Color(0xFFE5D7D9),
    surfaceBright = Color(0xFFFFF8F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFAEAEC),
    surfaceContainerHigh = Color(0xFFF5E4E7),
    surfaceContainerHighest = Color(0xFFEFDFE1),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
    inverseSurface = Color(0xFF372F30),
    inverseOnSurface = Color(0xFFFCEEED),
    inversePrimary = Color(0xFFFFB1C8)
)

private val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF60002E),
    primaryContainer = Color(0xFF821040),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFE5BDC6),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5B3F47),
    onSecondaryContainer = Color(0xFFFFD9E2),
    tertiary = Color(0xFFF0BD94),
    onTertiary = Color(0xFF47280A),
    tertiaryContainer = Color(0xFF613E1E),
    onTertiaryContainer = Color(0xFFFFDCC1),
    background = Color(0xFF181114),
    onBackground = Color(0xFFEFDFE1),
    surface = Color(0xFF181114),
    onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    surfaceDim = Color(0xFF181114),
    surfaceBright = Color(0xFF403739),
    surfaceContainerLowest = Color(0xFF130C0E),
    surfaceContainerLow = Color(0xFF211A1C),
    surfaceContainer = Color(0xFF251D20),
    surfaceContainerHigh = Color(0xFF30282B),
    surfaceContainerHighest = Color(0xFF3B3335),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
    inverseSurface = Color(0xFFEFDFE1),
    inverseOnSurface = Color(0xFF372F30),
    inversePrimary = Color(0xFF9F2A56)
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
