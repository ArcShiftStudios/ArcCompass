package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.AccentPreset
import com.example.data.AppThemeMode
import com.example.data.ArcCompassSettings

val ArcShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ArcCompassTheme(
    settings: ArcCompassSettings = ArcCompassSettings(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lowRam = remember(context) { isLowPerformanceDevice(context) }
    val systemDark = isSystemInDarkTheme()
    val useDark = when (settings.themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = when {
        settings.redScreenFilterEnabled -> nightVisionRedColorScheme()
        settings.dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val base = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (settings.highContrastEnabled) applyHighContrast(base, useDark) else base
        }
        useDark -> buildDarkScheme(settings.accentPreset, settings.highContrastEnabled)
        else -> buildLightScheme(settings.accentPreset, settings.highContrastEnabled)
    }

    val typography = if (settings.largeReadoutsEnabled) {
        ArcTypography.copy(
            displaySmall = ArcTypography.displaySmall.copy(
                fontSize = ArcTypography.displaySmall.fontSize * 1.12f
            ),
            headlineSmall = ArcTypography.headlineSmall.copy(
                fontSize = ArcTypography.headlineSmall.fontSize * 1.1f
            ),
            bodyLarge = ArcTypography.bodyLarge.copy(
                fontSize = ArcTypography.bodyLarge.fontSize * 1.08f
            ),
            bodyMedium = ArcTypography.bodyMedium.copy(
                fontSize = ArcTypography.bodyMedium.fontSize * 1.08f
            )
        )
    } else {
        ArcTypography
    }

    CompositionLocalProvider(
        LocalArcSettings provides settings,
        LocalLowRamDevice provides lowRam
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = ArcShapes,
            content = content
        )
    }
}

private fun buildDarkScheme(preset: AccentPreset, highContrast: Boolean): ColorScheme {
    val (primary, onPrimary, container, onContainer) = when (preset) {
        AccentPreset.COOL_BLUE -> Quad(
            AccentCoolBlueDark, Color(0xFF071B36), AccentCoolBlueContainerDark, Color(0xFFDCEAFF)
        )
        AccentPreset.MONOCHROME -> Quad(
            AccentMonoDark, Color(0xFF090B0F), AccentMonoContainerDark, Color(0xFFF8FAFC)
        )
        AccentPreset.AMBER -> Quad(
            AccentAmberDark, Color(0xFF2B1A00), AccentAmberContainerDark, Color(0xFFFEF3C7)
        )
        AccentPreset.EMERALD -> Quad(
            AccentEmeraldDark, Color(0xFF02261B), AccentEmeraldContainerDark, Color(0xFFD1FAE5)
        )
    }

    val base = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = primary.copy(alpha = 0.88f),
        onSecondary = onPrimary,
        secondaryContainer = ArcCharcoalSurfaceVariant,
        onSecondaryContainer = ArcCharcoalOnSurface,
        tertiary = ArcCharcoalOnSurfaceVariant,
        background = ArcCharcoalBackground,
        onBackground = ArcCharcoalOnSurface,
        surface = ArcCharcoalSurface,
        onSurface = ArcCharcoalOnSurface,
        surfaceVariant = ArcCharcoalSurfaceVariant,
        onSurfaceVariant = ArcCharcoalOnSurfaceVariant,
        outline = ArcCharcoalOutline
    )
    return if (highContrast) applyHighContrast(base, dark = true) else base
}

private fun buildLightScheme(preset: AccentPreset, highContrast: Boolean): ColorScheme {
    val (primary, onPrimary, container, onContainer) = when (preset) {
        AccentPreset.COOL_BLUE -> Quad(
            AccentCoolBlueLight, Color.White, AccentCoolBlueContainerLight, Color(0xFF0B2547)
        )
        AccentPreset.MONOCHROME -> Quad(
            AccentMonoLight, Color.White, AccentMonoContainerLight, Color(0xFF0F172A)
        )
        AccentPreset.AMBER -> Quad(
            AccentAmberLight, Color.White, AccentAmberContainerLight, Color(0xFF451A03)
        )
        AccentPreset.EMERALD -> Quad(
            AccentEmeraldLight, Color.White, AccentEmeraldContainerLight, Color(0xFF022C22)
        )
    }

    val base = lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = ArcAlabasterSurfaceVariant,
        onSecondaryContainer = ArcAlabasterOnSurface,
        tertiary = ArcAlabasterOnSurfaceVariant,
        background = ArcAlabasterBackground,
        onBackground = ArcAlabasterOnSurface,
        surface = ArcAlabasterSurface,
        onSurface = ArcAlabasterOnSurface,
        surfaceVariant = ArcAlabasterSurfaceVariant,
        onSurfaceVariant = ArcAlabasterOnSurfaceVariant,
        outline = ArcAlabasterOutline
    )
    return if (highContrast) applyHighContrast(base, dark = false) else base
}

private fun applyHighContrast(base: ColorScheme, dark: Boolean): ColorScheme {
    return if (dark) {
        base.copy(
            background = Color.Black,
            surface = Color(0xFF09090B),
            surfaceVariant = Color(0xFF141418),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFE2E8F0),
            outline = Color(0xFF94A3B8)
        )
    } else {
        base.copy(
            background = Color.White,
            surface = Color.White,
            surfaceVariant = Color(0xFFF1F5F9),
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color(0xFF1E293B),
            outline = Color(0xFF334155)
        )
    }
}

private fun nightVisionRedColorScheme(): ColorScheme = darkColorScheme(
    primary = NightRedPrimary,
    onPrimary = NightRedOnPrimary,
    primaryContainer = NightRedContainer,
    onPrimaryContainer = NightRedOnContainer,
    secondary = NightRedSecondary,
    onSecondary = NightRedOnPrimary,
    secondaryContainer = NightRedSurfaceVariant,
    onSecondaryContainer = NightRedOnSurface,
    tertiary = NightRedOnSurfaceVariant,
    background = NightRedBackground,
    onBackground = NightRedOnSurface,
    surface = NightRedSurface,
    onSurface = NightRedOnSurface,
    surfaceVariant = NightRedSurfaceVariant,
    onSurfaceVariant = NightRedOnSurfaceVariant,
    outline = NightRedOutline
)

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
