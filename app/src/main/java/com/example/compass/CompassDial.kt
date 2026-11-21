package com.example.compass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccentPreset
import com.example.data.DialStyle
import com.example.data.ExpressiveDialShape
import com.example.data.GeoNavigationMath
import com.example.data.HeadingFormat
import com.example.sensor.CompassHeadingState
import com.example.sensor.DiagnosticStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NorthIndicatorRed
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.resolveAccentColor
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Computes normalized radial multiplier for each Expressive dial silhouette at angle [thetaRad].
 * Keeping all 5 profiles bounded near 0.88..1.05 preserves dial balance and degree-tick alignment.
 */
fun expressiveShapeRadiusFactor(shape: ExpressiveDialShape, thetaRad: Double): Float {
    return when (shape) {
        ExpressiveDialShape.CIRCLE -> 0.96f
        ExpressiveDialShape.SOFT_SCALLOPED -> (0.94f + 0.045 * cos(16.0 * thetaRad)).toFloat()
        ExpressiveDialShape.ROUNDED_FLOWER -> {
            // Smooth 8-petal rounded blossom silhouette
            val petal = abs(cos(4.0 * thetaRad))
            (0.89f + 0.095 * (petal * petal)).toFloat()
        }
        ExpressiveDialShape.ORGANIC_WAVE -> {
            // Fluid harmonic wave silhouette
            (0.935f + 0.042 * cos(5.0 * thetaRad + 0.4) + 0.024 * sin(3.0 * thetaRad)).toFloat()
        }
        ExpressiveDialShape.GEOMETRIC -> {
            // Rounded 12-sided chamfered dodecagon
            val sector = (2.0 * PI) / 12.0
            val local = ((thetaRad % sector) + sector) % sector - sector / 2.0
            val poly = (cos(sector / 2.0) / cos(local)).toFloat()
            (0.92f * (0.65f + 0.35f * poly)).coerceIn(0.86f, 1.0f)
        }
    }
}

@Composable
fun CompassDial(
    headingDegrees: Float?,
    headingState: CompassHeadingState,
    dialStyle: DialStyle,
    expressiveShape: ExpressiveDialShape,
    expressiveAccentPreset: AccentPreset,
    headingFormat: HeadingFormat,
    showDecimals: Boolean,
    trueNorthActive: Boolean,
    reducedMotion: Boolean,
    largeReadouts: Boolean,
    nightFilterActive: Boolean,
    modifier: Modifier = Modifier
) {
    val targetAngle = headingDegrees ?: 0f
    var continuousTarget by remember { mutableFloatStateOf(-targetAngle) }

    LaunchedEffect(targetAngle, headingDegrees) {
        if (headingDegrees != null) {
            val currentMod = ((-continuousTarget % 360f) + 360f) % 360f
            val delta = GeoNavigationMath.shortestSignedDelta(currentMod, targetAngle)
            continuousTarget -= delta
        }
    }

    val animatedRotation by animateFloatAsState(
        targetValue = if (headingDegrees != null) continuousTarget else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "compass_dial_rotation"
    )

    // Smooth shape transition state for Expressive Dial only
    var fromShape by remember { mutableStateOf(expressiveShape) }
    var toShape by remember { mutableStateOf(expressiveShape) }
    val shapeMorphProgress = remember { Animatable(1f) }

    LaunchedEffect(expressiveShape, reducedMotion) {
        if (expressiveShape != toShape) {
            fromShape = toShape
            toShape = expressiveShape
            if (reducedMotion) {
                shapeMorphProgress.snapTo(1f)
            } else {
                shapeMorphProgress.snapTo(0f)
                shapeMorphProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val northColor = if (nightFilterActive) colorScheme.primary else NorthIndicatorRed
    val precisionAccent = colorScheme.primary
    val expressiveAccent = resolveAccentColor(expressiveAccentPreset, isDark, nightFilterActive)
    val activeAccent = if (dialStyle == DialStyle.EXPRESSIVE) expressiveAccent else precisionAccent

    val surfaceFill = colorScheme.surface
    val surfaceVariantFill = colorScheme.surfaceVariant
    val primaryInk = colorScheme.onSurface
    val mutedInk = colorScheme.onSurfaceVariant
    val outlineColor = colorScheme.outline

    val degreeTextStyle = remember(primaryInk, largeReadouts) {
        TextStyle(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (largeReadouts) 17.sp else 15.sp,
            color = primaryInk
        )
    }
    val minorLabelStyle = remember(mutedInk, largeReadouts) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (largeReadouts) 12.sp else 11.sp,
            color = mutedInk
        )
    }
    val cardinalStyle = remember(primaryInk) {
        TextStyle(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = primaryInk
        )
    }
    val northCardinalStyle = remember(northColor) {
        TextStyle(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = northColor
        )
    }

    Box(
        modifier = modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(2.dp)
            .testTag("compass_dial_canvas"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (size.minDimension / 2f) * 0.96f
            val center = Offset(cx, cy)

            if (dialStyle == DialStyle.EXPRESSIVE) {
                drawExpressiveDial(
                    center = center,
                    radius = radius,
                    rotationDegrees = animatedRotation,
                    fromShape = fromShape,
                    toShape = toShape,
                    morphFraction = shapeMorphProgress.value,
                    isSupported = headingState.isSupported,
                    isDark = isDark,
                    nightFilterActive = nightFilterActive,
                    surfaceFill = surfaceVariantFill,
                    accent = expressiveAccent,
                    northColor = northColor,
                    primaryInk = primaryInk,
                    mutedInk = mutedInk,
                    outlineColor = outlineColor,
                    textMeasurer = textMeasurer,
                    cardinalStyle = cardinalStyle,
                    northCardinalStyle = northCardinalStyle,
                    intercardinalStyle = minorLabelStyle
                )
            } else {
                drawPrecisionDial(
                    center = center,
                    radius = radius,
                    rotationDegrees = animatedRotation,
                    isSupported = headingState.isSupported,
                    isDark = isDark,
                    nightFilterActive = nightFilterActive,
                    surfaceFill = surfaceFill,
                    accent = precisionAccent,
                    northColor = northColor,
                    primaryInk = primaryInk,
                    mutedInk = mutedInk,
                    outlineColor = outlineColor,
                    textMeasurer = textMeasurer,
                    degreeLabelStyle = minorLabelStyle,
                    cardinalStyle = degreeTextStyle,
                    northCardinalStyle = northCardinalStyle
                )
            }

            // Fixed frosted lubber indicator at 12 o'clock
            val lubberY = cy - radius * (if (dialStyle == DialStyle.EXPRESSIVE) 0.72f else 0.74f)
            val pointerSize = radius * 0.064f
            val pointerPath = Path().apply {
                moveTo(cx, lubberY - pointerSize * 0.80f)
                lineTo(cx - pointerSize * 0.68f, lubberY + pointerSize * 0.54f)
                lineTo(cx + pointerSize * 0.68f, lubberY + pointerSize * 0.54f)
                close()
            }
            drawPath(
                path = pointerPath,
                color = if (headingState.isSupported) activeAccent else mutedInk.copy(alpha = 0.45f)
            )
        }

        // Crisp upright central readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                !headingState.isSupported -> {
                    Text(
                        text = "--°",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = if (largeReadouts) 58.sp else 52.sp,
                            letterSpacing = (-1.5).sp,
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = mutedInk,
                        modifier = Modifier.testTag("compass_heading_value")
                    )
                    Text(
                        text = "UNSUPPORTED",
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedInk
                    )
                }
                headingDegrees == null -> {
                    Text(
                        text = "•••",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 52.sp
                        ),
                        color = mutedInk,
                        modifier = Modifier.testTag("compass_heading_value")
                    )
                    Text(
                        text = if (headingState.status == DiagnosticStatus.INITIALIZING) "CALIBRATING" else "WAITING",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedInk
                    )
                }
                else -> {
                    val primaryText = when (headingFormat) {
                        HeadingFormat.AZIMUTH -> if (showDecimals) {
                            String.format(Locale.US, "%.1f°", headingDegrees)
                        } else {
                            "${headingDegrees.roundToInt() % 360}°"
                        }
                        HeadingFormat.QUADRANT -> GeoNavigationMath.formatQuadrantBearing(
                            degrees = headingDegrees,
                            showDecimals = showDecimals
                        )
                    }
                    val secondaryText = when (headingFormat) {
                        HeadingFormat.AZIMUTH -> GeoNavigationMath.cardinal16(headingDegrees)
                        HeadingFormat.QUADRANT -> if (showDecimals) {
                            String.format(Locale.US, "%.1f° %s", headingDegrees, GeoNavigationMath.cardinal16(headingDegrees))
                        } else {
                            "${headingDegrees.roundToInt() % 360}° ${GeoNavigationMath.cardinal16(headingDegrees)}"
                        }
                    }
                    Text(
                        text = primaryText,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = when {
                                headingFormat == HeadingFormat.QUADRANT && largeReadouts -> 40.sp
                                headingFormat == HeadingFormat.QUADRANT -> 36.sp
                                largeReadouts -> 64.sp
                                else -> 58.sp
                            },
                            letterSpacing = (-1.5).sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = primaryInk,
                        modifier = Modifier.testTag("compass_heading_value")
                    )
                    Text(
                        text = "$secondaryText • ${if (trueNorthActive) "TRUE" else "MAG"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = activeAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("compass_direction_label")
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawExpressiveDial(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    fromShape: ExpressiveDialShape,
    toShape: ExpressiveDialShape,
    morphFraction: Float,
    isSupported: Boolean,
    isDark: Boolean,
    nightFilterActive: Boolean,
    surfaceFill: Color,
    accent: Color,
    northColor: Color,
    primaryInk: Color,
    mutedInk: Color,
    outlineColor: Color,
    textMeasurer: TextMeasurer,
    cardinalStyle: TextStyle,
    northCardinalStyle: TextStyle,
    intercardinalStyle: TextStyle
) {
    val alphaFactor = if (isSupported) 1f else 0.4f

    fun blendedFactor(thetaRad: Double): Float {
        val f1 = expressiveShapeRadiusFactor(fromShape, thetaRad)
        val f2 = expressiveShapeRadiusFactor(toShape, thetaRad)
        return f1 + (f2 - f1) * morphFraction
    }

    rotate(degrees = rotationDegrees, pivot = center) {
        val shapePath = buildBlendedExpressivePath(
            center = center,
            baseRadius = radius * 0.965f,
            factorAt = ::blendedFactor
        )

        // Layered translucent frosted-glass body with sculptural depth & accent refraction
        drawPath(
            path = shapePath,
            brush = Brush.radialGradient(
                colors = listOf(
                    surfaceFill.copy(alpha = (if (isDark) 0.52f else 0.72f) * alphaFactor),
                    accent.copy(alpha = (if (isDark) 0.24f else 0.16f) * alphaFactor)
                ),
                center = center,
                radius = radius
            )
        )
        drawPath(
            path = shapePath,
            brush = Brush.linearGradient(
                colors = listOf(
                    if (nightFilterActive) accent.copy(alpha = 0.14f * alphaFactor)
                    else Color.White.copy(alpha = (if (isDark) 0.10f else 0.45f) * alphaFactor),
                    Color.Transparent
                ),
                start = Offset(center.x - radius, center.y - radius),
                end = Offset(center.x + radius, center.y + radius)
            )
        )
        val rimHighlight = when {
            nightFilterActive -> accent.copy(alpha = 0.58f * alphaFactor)
            isDark -> Color.White.copy(alpha = 0.28f * alphaFactor)
            else -> outlineColor.copy(alpha = 0.55f * alphaFactor)
        }
        drawPath(
            path = shapePath,
            color = rimHighlight,
            style = Stroke(width = 1.6.dp.toPx())
        )

        // Subtle frosted inner lens ring
        drawCircle(
            color = accent.copy(alpha = 0.24f * alphaFactor),
            radius = radius * 0.53f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )

        // 72 degree ticks following the selected Expressive shape silhouette
        for (deg in 0 until 360 step 5) {
            val isMajor = deg % 30 == 0
            val isNorth = deg == 0
            val rad = Math.toRadians(deg - 90.0)
            val contourScale = blendedFactor(rad)
            val outerR = radius * 0.895f * contourScale
            val tickLen = radius * if (isMajor) 0.095f else 0.048f
            val innerR = outerR - tickLen

            val start = Offset(
                center.x + (outerR * cos(rad)).toFloat(),
                center.y + (outerR * sin(rad)).toFloat()
            )
            val end = Offset(
                center.x + (innerR * cos(rad)).toFloat(),
                center.y + (innerR * sin(rad)).toFloat()
            )

            drawLine(
                color = when {
                    isNorth -> northColor.copy(alpha = alphaFactor)
                    isMajor -> accent.copy(alpha = 0.95f * alphaFactor)
                    else -> mutedInk.copy(alpha = 0.48f * alphaFactor)
                },
                start = start,
                end = end,
                strokeWidth = if (isNorth) 3.2.dp.toPx() else if (isMajor) 2.2.dp.toPx() else 1.1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Cardinal & intercardinal labels tucked cleanly along the inner ring
        val labels = listOf(
            0 to "N", 45 to "NE", 90 to "E", 135 to "SE",
            180 to "S", 225 to "SW", 270 to "W", 315 to "NW"
        )
        labels.forEach { (deg, text) ->
            val rad = Math.toRadians(deg - 90.0)
            val labelR = radius * 0.62f * (0.5f + 0.5f * blendedFactor(rad))
            val pos = Offset(
                center.x + (labelR * cos(rad)).toFloat(),
                center.y + (labelR * sin(rad)).toFloat()
            )
            val style = when {
                deg == 0 -> northCardinalStyle.copy(color = northColor.copy(alpha = alphaFactor))
                deg % 90 == 0 -> cardinalStyle.copy(color = primaryInk.copy(alpha = alphaFactor))
                else -> intercardinalStyle.copy(color = mutedInk.copy(alpha = alphaFactor))
            }
            drawCenteredUprightText(
                textMeasurer = textMeasurer,
                text = text,
                style = style,
                anchor = pos,
                counterRotateDegrees = -rotationDegrees
            )
        }
    }
}

private fun DrawScope.drawPrecisionDial(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    isSupported: Boolean,
    isDark: Boolean,
    nightFilterActive: Boolean,
    surfaceFill: Color,
    accent: Color,
    northColor: Color,
    primaryInk: Color,
    mutedInk: Color,
    outlineColor: Color,
    textMeasurer: TextMeasurer,
    degreeLabelStyle: TextStyle,
    cardinalStyle: TextStyle,
    northCardinalStyle: TextStyle
) {
    val alphaFactor = if (isSupported) 1f else 0.4f

    // Translucent frosted glass precision disc & dual instrument bezel rings
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                surfaceFill.copy(alpha = (if (isDark) 0.55f else 0.78f) * alphaFactor),
                surfaceFill.copy(alpha = (if (isDark) 0.36f else 0.58f) * alphaFactor)
            ),
            center = center,
            radius = radius * 0.89f
        ),
        radius = radius * 0.89f,
        center = center
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                if (nightFilterActive) accent.copy(alpha = 0.12f * alphaFactor)
                else Color.White.copy(alpha = (if (isDark) 0.08f else 0.42f) * alphaFactor),
                Color.Transparent
            ),
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        ),
        radius = radius * 0.89f,
        center = center
    )
    val rimColor = when {
        nightFilterActive -> accent.copy(alpha = 0.58f * alphaFactor)
        isDark -> Color.White.copy(alpha = 0.24f * alphaFactor)
        else -> outlineColor.copy(alpha = 0.55f * alphaFactor)
    }
    drawCircle(
        color = rimColor,
        radius = radius * 0.89f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )
    drawCircle(
        color = outlineColor.copy(alpha = 0.28f * alphaFactor),
        radius = radius * 0.54f,
        center = center,
        style = Stroke(width = 1.0.dp.toPx())
    )

    rotate(degrees = rotationDegrees, pivot = center) {
        // Three distinct levels of degree markings across full 360°:
        // 1. Minor ticks: individual 1° marks
        // 2. Intermediate ticks: 5° and 10° subdivisions
        // 3. Major ticks: every 30° (visibly thicker and longer)
        for (deg in 0 until 360) {
            val isMajor30 = deg % 30 == 0
            val isTen = deg % 10 == 0
            val isFive = deg % 5 == 0
            val isNorth = deg == 0
            val rad = Math.toRadians(deg - 90.0)
            val outerR = radius * 0.875f
            val innerR = radius * when {
                isMajor30 -> 0.735f
                isTen -> 0.790f
                isFive -> 0.815f
                else -> 0.840f
            }

            val start = Offset(
                center.x + (outerR * cos(rad)).toFloat(),
                center.y + (outerR * sin(rad)).toFloat()
            )
            val end = Offset(
                center.x + (innerR * cos(rad)).toFloat(),
                center.y + (innerR * sin(rad)).toFloat()
            )

            drawLine(
                color = when {
                    isNorth -> northColor.copy(alpha = alphaFactor)
                    isMajor30 -> primaryInk.copy(alpha = 0.96f * alphaFactor)
                    isTen -> primaryInk.copy(alpha = 0.74f * alphaFactor)
                    isFive -> mutedInk.copy(alpha = 0.62f * alphaFactor)
                    else -> mutedInk.copy(alpha = 0.30f * alphaFactor)
                },
                start = start,
                end = end,
                strokeWidth = when {
                    isNorth -> 3.3.dp.toPx()
                    isMajor30 -> 2.6.dp.toPx()
                    isTen -> 1.5.dp.toPx()
                    isFive -> 1.1.dp.toPx()
                    else -> 0.68.dp.toPx()
                },
                cap = StrokeCap.Butt
            )

            if (isMajor30) {
                val degLabelR = radius * 0.955f
                val degPos = Offset(
                    center.x + (degLabelR * cos(rad)).toFloat(),
                    center.y + (degLabelR * sin(rad)).toFloat()
                )
                drawCenteredUprightText(
                    textMeasurer = textMeasurer,
                    text = deg.toString(),
                    style = degreeLabelStyle.copy(
                        color = if (isNorth) northColor.copy(alpha = alphaFactor) else mutedInk.copy(alpha = alphaFactor)
                    ),
                    anchor = degPos,
                    counterRotateDegrees = -rotationDegrees
                )
            }
        }

        val cardinals = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        cardinals.forEach { (deg, label) ->
            val rad = Math.toRadians(deg - 90.0)
            val r = radius * 0.635f
            val pos = Offset(
                center.x + (r * cos(rad)).toFloat(),
                center.y + (r * sin(rad)).toFloat()
            )
            val style = if (deg == 0) {
                northCardinalStyle.copy(color = northColor.copy(alpha = alphaFactor))
            } else {
                cardinalStyle.copy(color = primaryInk.copy(alpha = alphaFactor))
            }
            drawCenteredUprightText(
                textMeasurer = textMeasurer,
                text = label,
                style = style,
                anchor = pos,
                counterRotateDegrees = -rotationDegrees
            )
        }

        // Crisp inner reticle crosshair
        drawLine(
            color = accent.copy(alpha = 0.20f * alphaFactor),
            start = Offset(center.x - radius * 0.44f, center.y),
            end = Offset(center.x + radius * 0.44f, center.y),
            strokeWidth = 0.85.dp.toPx()
        )
        drawLine(
            color = accent.copy(alpha = 0.20f * alphaFactor),
            start = Offset(center.x, center.y - radius * 0.44f),
            end = Offset(center.x, center.y + radius * 0.44f),
            strokeWidth = 0.85.dp.toPx()
        )
    }
}

private fun DrawScope.drawCenteredUprightText(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    anchor: Offset,
    counterRotateDegrees: Float
) {
    val result = textMeasurer.measure(text = text, style = style)
    val topLeft = Offset(
        x = anchor.x - result.size.width / 2f,
        y = anchor.y - result.size.height / 2f
    )
    rotate(degrees = counterRotateDegrees, pivot = anchor) {
        drawText(textLayoutResult = result, topLeft = topLeft)
    }
}

private inline fun buildBlendedExpressivePath(
    center: Offset,
    baseRadius: Float,
    factorAt: (Double) -> Float
): Path {
    val steps = 180
    return Path().apply {
        for (i in 0..steps) {
            val theta = (2.0 * PI * i) / steps
            val r = baseRadius * factorAt(theta)
            val x = center.x + (r * cos(theta)).toFloat()
            val y = center.y + (r * sin(theta)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
