package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val FredokaNormal = FontFamily(
    Font(R.font.fredoka_light, FontWeight.Light),
    Font(R.font.fredoka_regular, FontWeight.Normal),
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold)
)

val FredokaCondensed = FontFamily(
    Font(R.font.fredoka_condensed_light, FontWeight.Light),
    Font(R.font.fredoka_condensed_regular, FontWeight.Normal),
    Font(R.font.fredoka_condensed_medium, FontWeight.Medium),
    Font(R.font.fredoka_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_condensed_bold, FontWeight.Bold)
)

val FredokaSemiCondensed = FontFamily(
    Font(R.font.fredoka_semicondensed_light, FontWeight.Light),
    Font(R.font.fredoka_semicondensed_regular, FontWeight.Normal),
    Font(R.font.fredoka_semicondensed_medium, FontWeight.Medium),
    Font(R.font.fredoka_semicondensed_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_semicondensed_bold, FontWeight.Bold)
)

val FredokaExpanded = FontFamily(
    Font(R.font.fredoka_expanded_light, FontWeight.Light),
    Font(R.font.fredoka_expanded_regular, FontWeight.Normal),
    Font(R.font.fredoka_expanded_medium, FontWeight.Medium),
    Font(R.font.fredoka_expanded_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_expanded_bold, FontWeight.Bold)
)

val FredokaSemiExpanded = FontFamily(
    Font(R.font.fredoka_semiexpanded_light, FontWeight.Light),
    Font(R.font.fredoka_semiexpanded_regular, FontWeight.Normal),
    Font(R.font.fredoka_semiexpanded_medium, FontWeight.Medium),
    Font(R.font.fredoka_semiexpanded_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_semiexpanded_bold, FontWeight.Bold)
)

/**
 * Reserved exclusively for RAW sensor numeric telemetry columns.
 * Never use for ordinary interface labels, headings, descriptions, or buttons.
 */
val RawTelemetryMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

@Immutable
data class ArcTextRoles(
    val display: TextStyle,
    val heading: TextStyle,
    val measurement: TextStyle,
    val compassDirection: TextStyle,
    val navigation: TextStyle,
    val settings: TextStyle,
    val technical: TextStyle,
    val description: TextStyle,
    val button: TextStyle,
    val rawNumeric: TextStyle
)

fun buildArcTextRoles(largeReadouts: Boolean = false): ArcTextRoles {
    val readoutScale = if (largeReadouts) 1.12f else 1.0f
    val bodyScale = if (largeReadouts) 1.08f else 1.0f
    return ArcTextRoles(
        display = TextStyle(
            fontFamily = FredokaExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = (54f * readoutScale).sp,
            lineHeight = (60f * readoutScale).sp,
            letterSpacing = (-0.8).sp
        ),
        heading = TextStyle(
            fontFamily = FredokaSemiExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = (22f * bodyScale).sp,
            lineHeight = (28f * bodyScale).sp,
            letterSpacing = (-0.2).sp
        ),
        measurement = TextStyle(
            fontFamily = FredokaSemiExpanded,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18f * readoutScale).sp,
            lineHeight = (24f * readoutScale).sp
        ),
        compassDirection = TextStyle(
            fontFamily = FredokaExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = (18f * readoutScale).sp,
            lineHeight = (22f * readoutScale).sp
        ),
        navigation = TextStyle(
            fontFamily = FredokaSemiCondensed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp
        ),
        settings = TextStyle(
            fontFamily = FredokaNormal,
            fontWeight = FontWeight.Medium,
            fontSize = (15.5f * bodyScale).sp,
            lineHeight = (21f * bodyScale).sp
        ),
        technical = TextStyle(
            fontFamily = FredokaSemiCondensed,
            fontWeight = FontWeight.Medium,
            fontSize = (12f * bodyScale).sp,
            lineHeight = (16f * bodyScale).sp
        ),
        description = TextStyle(
            fontFamily = FredokaNormal,
            fontWeight = FontWeight.Normal,
            fontSize = (13.5f * bodyScale).sp,
            lineHeight = (19.5f * bodyScale).sp
        ),
        button = TextStyle(
            fontFamily = FredokaSemiCondensed,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13.5f * bodyScale).sp,
            lineHeight = (18f * bodyScale).sp
        ),
        rawNumeric = TextStyle(
            fontFamily = RawTelemetryMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12.5f * bodyScale).sp,
            lineHeight = (17f * bodyScale).sp
        )
    )
}

val DefaultArcTextRoles = buildArcTextRoles(largeReadouts = false)

val LocalArcTextRoles = staticCompositionLocalOf { DefaultArcTextRoles }

val MaterialTheme.arcTextRoles: ArcTextRoles
    @Composable
    @ReadOnlyComposable
    get() = LocalArcTextRoles.current

fun buildArcTypography(largeReadouts: Boolean = false): Typography {
    val readoutScale = if (largeReadouts) 1.12f else 1.0f
    val bodyScale = if (largeReadouts) 1.08f else 1.0f
    return Typography(
        displayLarge = TextStyle(
            fontFamily = FredokaExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = (52f * readoutScale).sp,
            lineHeight = (58f * readoutScale).sp,
            letterSpacing = (-0.8).sp
        ),
        displayMedium = TextStyle(
            fontFamily = FredokaExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = (42f * readoutScale).sp,
            lineHeight = (48f * readoutScale).sp,
            letterSpacing = (-0.6).sp
        ),
        displaySmall = TextStyle(
            fontFamily = FredokaExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = (34f * readoutScale).sp,
            lineHeight = (40f * readoutScale).sp,
            letterSpacing = (-0.4).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = FredokaSemiExpanded,
            fontWeight = FontWeight.Bold,
            fontSize = (28f * bodyScale).sp,
            lineHeight = (34f * bodyScale).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = FredokaSemiExpanded,
            fontWeight = FontWeight.SemiBold,
            fontSize = (24f * bodyScale).sp,
            lineHeight = (30f * bodyScale).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = FredokaSemiExpanded,
            fontWeight = FontWeight.SemiBold,
            fontSize = (21f * bodyScale).sp,
            lineHeight = (27f * bodyScale).sp,
            letterSpacing = (-0.2).sp
        ),
        titleLarge = TextStyle(
            fontFamily = FredokaSemiExpanded,
            fontWeight = FontWeight.SemiBold,
            fontSize = (19f * bodyScale).sp,
            lineHeight = (25f * bodyScale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = FredokaSemiExpanded,
            fontWeight = FontWeight.Medium,
            fontSize = (16f * bodyScale).sp,
            lineHeight = (22f * bodyScale).sp
        ),
        titleSmall = TextStyle(
            fontFamily = FredokaNormal,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14.5f * bodyScale).sp,
            lineHeight = (20f * bodyScale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FredokaNormal,
            fontWeight = FontWeight.Medium,
            fontSize = (15.5f * bodyScale).sp,
            lineHeight = (22f * bodyScale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FredokaNormal,
            fontWeight = FontWeight.Normal,
            fontSize = (13.5f * bodyScale).sp,
            lineHeight = (19.5f * bodyScale).sp
        ),
        bodySmall = TextStyle(
            fontFamily = FredokaNormal,
            fontWeight = FontWeight.Normal,
            fontSize = (12.5f * bodyScale).sp,
            lineHeight = (17.5f * bodyScale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = FredokaSemiCondensed,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13.5f * bodyScale).sp,
            lineHeight = (18f * bodyScale).sp
        ),
        labelMedium = TextStyle(
            fontFamily = FredokaSemiCondensed,
            fontWeight = FontWeight.Medium,
            fontSize = (12f * bodyScale).sp,
            lineHeight = (16f * bodyScale).sp
        ),
        labelSmall = TextStyle(
            fontFamily = FredokaSemiCondensed,
            fontWeight = FontWeight.Normal,
            fontSize = (11f * bodyScale).sp,
            lineHeight = (14.5f * bodyScale).sp
        )
    )
}

val ArcTypography = buildArcTypography(largeReadouts = false)
