package com.example.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AccentPreset
import com.example.data.ArcCompassSettings

val LocalArcSettings = compositionLocalOf { ArcCompassSettings() }
val LocalLowRamDevice = compositionLocalOf { false }

enum class GlassTier {
    CARD,
    SHEET,
    FLOATING_CHROME,
    DIALOG
}

fun isLowPerformanceDevice(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return am?.isLowRamDevice == true || Build.VERSION.SDK_INT < Build.VERSION_CODES.S
}

fun resolveAccentColor(
    preset: AccentPreset,
    isDark: Boolean,
    nightFilterActive: Boolean
): Color {
    if (nightFilterActive) return NightRedPrimary
    return when (preset) {
        AccentPreset.COOL_BLUE -> if (isDark) AccentCoolBlueDark else AccentCoolBlueLight
        AccentPreset.MONOCHROME -> if (isDark) AccentMonoDark else AccentMonoLight
        AccentPreset.AMBER -> if (isDark) AccentAmberDark else AccentAmberLight
        AccentPreset.EMERALD -> if (isDark) AccentEmeraldDark else AccentEmeraldLight
    }
}

/**
 * Multi-layered atmospheric light field behind ArcCompass frosted-glass surfaces.
 * Uses Android 12+ native RenderEffect blur strictly on this background light field
 * (never blurring dial ticks, text, or readouts) with an automatic radial-gradient fallback
 * on pre-Android 12, low-RAM devices, High Contrast, or when blur is disabled.
 */
@Composable
fun FrostedAmbientBackdrop(
    modifier: Modifier = Modifier,
    accentOverride: Color? = null
) {
    val settings = LocalArcSettings.current
    val lowRam = LocalLowRamDevice.current
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val primaryGlow = accentOverride ?: colorScheme.primary
    val secondaryGlow = when {
        settings.redScreenFilterEnabled -> NightRedSecondary
        isDark -> Color(0xFF1D4ED8)
        else -> colorScheme.primaryContainer
    }

    val useNativeBlur = settings.frostedBlurEnabled &&
        !lowRam &&
        !settings.highContrastEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        if (!settings.highContrastEnabled) {
            val blurMod = if (useNativeBlur) Modifier.blur(48.dp) else Modifier
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurMod)
            ) {
                // Subtle diagonal atmospheric sheen across viewport so frosted sheets/cards show real tonal depth
                val horizonAlpha = if (settings.redScreenFilterEnabled) 0.14f else if (isDark) 0.16f else 0.10f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = horizonAlpha * 0.75f),
                            Color.Transparent,
                            secondaryGlow.copy(alpha = horizonAlpha * 0.90f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )

                val topOrbCenter = Offset(size.width * 0.18f, size.height * 0.14f)
                val instrumentHalo = Offset(size.width * 0.50f, size.height * 0.43f)
                val bottomOrbCenter = Offset(size.width * 0.84f, size.height * 0.80f)

                val topAlpha = if (settings.redScreenFilterEnabled) 0.18f else if (isDark) 0.24f else 0.13f
                val haloAlpha = if (settings.redScreenFilterEnabled) 0.12f else if (isDark) 0.17f else 0.10f
                val bottomAlpha = if (settings.redScreenFilterEnabled) 0.16f else if (isDark) 0.28f else 0.14f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow.copy(alpha = topAlpha), Color.Transparent),
                        center = topOrbCenter,
                        radius = size.minDimension * 0.78f
                    ),
                    radius = size.minDimension * 0.78f,
                    center = topOrbCenter
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryGlow.copy(alpha = haloAlpha),
                            secondaryGlow.copy(alpha = haloAlpha * 0.4f),
                            Color.Transparent
                        ),
                        center = instrumentHalo,
                        radius = size.minDimension * 0.68f
                    ),
                    radius = size.minDimension * 0.68f,
                    center = instrumentHalo
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow.copy(alpha = bottomAlpha), Color.Transparent),
                        center = bottomOrbCenter,
                        radius = size.minDimension * 0.85f
                    ),
                    radius = size.minDimension * 0.85f,
                    center = bottomOrbCenter
                )
            }
        }
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.CARD,
    shape: Shape = MaterialTheme.shapes.large,
    tintColor: Color? = null,
    borderColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val settings = LocalArcSettings.current
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f

    val hasTranslucentOverlayTint = tintColor != null && tintColor.alpha < 0.99f
    val baseSurface = when {
        tintColor == null -> when (tier) {
            GlassTier.CARD -> colorScheme.surface
            GlassTier.SHEET -> colorScheme.surface
            GlassTier.FLOATING_CHROME -> colorScheme.surfaceVariant
            GlassTier.DIALOG -> colorScheme.surface
        }
        hasTranslucentOverlayTint -> colorScheme.surface
        else -> tintColor
    }

    val fillAlpha = remember(tier, settings.highContrastEnabled, settings.redScreenFilterEnabled, isDark) {
        if (settings.highContrastEnabled) {
            0.96f
        } else {
            when (tier) {
                GlassTier.CARD -> if (isDark) 0.54f else 0.76f
                GlassTier.SHEET -> if (isDark) 0.68f else 0.86f
                GlassTier.FLOATING_CHROME -> if (isDark) 0.60f else 0.82f
                GlassTier.DIALOG -> if (isDark) 0.94f else 0.97f
            }
        }
    }

    val resolvedContentColor = contentColor ?: when {
        tintColor != null && !hasTranslucentOverlayTint -> {
            contentColorFor(tintColor).takeIf { it != Color.Unspecified } ?: colorScheme.onSurface
        }
        else -> colorScheme.onSurface
    }

    val specularTop = remember(settings.redScreenFilterEnabled, settings.highContrastEnabled, isDark, tier) {
        when {
            settings.redScreenFilterEnabled -> NightRedPrimary.copy(alpha = 0.11f)
            settings.highContrastEnabled -> Color.Transparent
            isDark -> Color.White.copy(
                alpha = when (tier) {
                    GlassTier.FLOATING_CHROME -> 0.12f
                    GlassTier.SHEET -> 0.10f
                    GlassTier.DIALOG -> 0.08f
                    GlassTier.CARD -> 0.08f
                }
            )
            else -> Color.White.copy(alpha = 0.52f)
        }
    }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            baseSurface.copy(alpha = (fillAlpha + 0.08f).coerceAtMost(0.96f)),
            baseSurface.copy(alpha = fillAlpha),
            baseSurface.copy(alpha = (fillAlpha - 0.05f).coerceAtLeast(0.42f))
        )
    )

    val resolvedBorder = borderColor ?: when {
        settings.highContrastEnabled -> colorScheme.outline
        settings.redScreenFilterEnabled -> NightRedOutline.copy(alpha = 0.85f)
        isDark -> Color.White.copy(
            alpha = when (tier) {
                GlassTier.FLOATING_CHROME -> 0.28f
                GlassTier.SHEET -> 0.26f
                GlassTier.DIALOG -> 0.24f
                GlassTier.CARD -> 0.19f
            }
        )
        else -> colorScheme.outline.copy(alpha = 0.34f)
    }

    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            resolvedBorder,
            resolvedBorder.copy(alpha = resolvedBorder.alpha * 0.45f)
        )
    )

    val shadowMod = if (elevation > 0.dp && !settings.highContrastEnabled) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.14f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.14f)
        )
    } else {
        Modifier
    }

    val overlayMod = if (hasTranslucentOverlayTint && tintColor != null) {
        Modifier.background(tintColor.copy(alpha = tintColor.alpha.coerceAtMost(0.24f)))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowMod)
            .clip(shape)
            .background(gradientBrush)
            .then(overlayMod)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        specularTop,
                        specularTop.copy(alpha = specularTop.alpha * 0.25f),
                        Color.Transparent
                    )
                )
            )
            .border(width = 1.dp, brush = borderBrush, shape = shape)
    ) {
        CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.CARD,
    shape: Shape = MaterialTheme.shapes.large,
    tintColor: Color? = null,
    borderColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassSurface(
        modifier = modifier,
        tier = tier,
        shape = shape,
        tintColor = tintColor,
        borderColor = borderColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    tintColor: Color? = null,
    borderColor: Color? = null,
    contentColor: Color = tintColor?.let {
        contentColorFor(it).takeIf { resolved -> resolved != Color.Unspecified }
    } ?: MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = contentColor,
        modifier = modifier
    ) {
        GlassSurface(
            tier = GlassTier.FLOATING_CHROME,
            shape = RoundedCornerShape(50),
            tintColor = tintColor,
            borderColor = borderColor,
            contentColor = contentColor,
            content = content
        )
    }
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        GlassSurface(
            tier = GlassTier.DIALOG,
            shape = RoundedCornerShape(26.dp),
            elevation = 18.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            Surface(
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    content = content
                )
            }
        }
    }
}
