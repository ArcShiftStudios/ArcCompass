package com.example.data

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geographic, bearing, and directional formatting helpers used by Compass and ArcPark.
 */
object GeoNavigationMath {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private val CARDINALS_16 = arrayOf(
        "N", "NNE", "NE", "ENE",
        "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW",
        "W", "WNW", "NW", "NNW"
    )
    private val CARDINALS_8 = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun normalizeDegrees(deg: Float): Float {
        val m = deg % 360f
        return if (m < 0f) m + 360f else m
    }

    fun shortestSignedDelta(fromDeg: Float, toDeg: Float): Float {
        return ((toDeg - fromDeg + 540f) % 360f) - 180f
    }

    fun cardinal16(degrees: Float): String {
        val norm = normalizeDegrees(degrees)
        val index = ((norm + 11.25f) / 22.5f).toInt() % 16
        return CARDINALS_16[index]
    }

    fun cardinal8(degrees: Float): String {
        val norm = normalizeDegrees(degrees)
        val index = ((norm + 22.5f) / 45f).toInt() % 8
        return CARDINALS_8[index]
    }

    /**
     * Formats azimuth into classic surveyor/compass quadrant notation (e.g., "S 68° E").
     */
    fun formatQuadrantBearing(degrees: Float, showDecimals: Boolean = false): String {
        val norm = normalizeDegrees(degrees)
        val rounded = if (showDecimals) norm else norm.roundToInt().toFloat() % 360f
        return when {
            rounded == 0f || rounded == 360f -> "N"
            rounded == 90f -> "E"
            rounded == 180f -> "S"
            rounded == 270f -> "W"
            rounded < 90f -> formatQuad("N", rounded, "E", showDecimals)
            rounded < 180f -> formatQuad("S", 180f - rounded, "E", showDecimals)
            rounded < 270f -> formatQuad("S", rounded - 180f, "W", showDecimals)
            else -> formatQuad("N", 360f - rounded, "W", showDecimals)
        }
    }

    private fun formatQuad(ns: String, angle: Float, ew: String, decimals: Boolean): String {
        return if (decimals) {
            String.format(Locale.US, "%s %.1f° %s", ns, angle, ew)
        } else {
            String.format(Locale.US, "%s %d° %s", ns, angle.roundToInt(), ew)
        }
    }

    fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun initialBearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val theta = atan2(y, x) * (180.0 / PI)
        return normalizeDegrees(theta.toFloat())
    }

    /**
     * Signed turn angle in [-180, 180]:
     * Positive = turn right, Negative = turn left, 0 = straight ahead.
     */
    fun relativeTurnDegrees(targetBearing: Float, phoneHeading: Float): Float {
        return shortestSignedDelta(phoneHeading, targetBearing)
    }

    fun relativeDirectionPrompt(relativeSignedDeg: Float, distanceMeters: Double): String {
        if (distanceMeters <= 8.0) return "Near your parking spot"
        val absDeg = abs(relativeSignedDeg)
        return when {
            absDeg <= 18f -> "Straight ahead"
            absDeg <= 50f -> if (relativeSignedDeg > 0) "Slightly right" else "Slightly left"
            absDeg <= 125f -> if (relativeSignedDeg > 0) "To your right" else "To your left"
            else -> "Behind you"
        }
    }

    fun formatDistance(meters: Double, imperial: Boolean): String {
        return if (imperial) {
            val feet = meters * 3.28084
            if (feet < 1000.0) {
                String.format(Locale.US, "%.0f ft", feet)
            } else {
                String.format(Locale.US, "%.2f mi", meters / 1609.344)
            }
        } else {
            if (meters < 1000.0) {
                String.format(Locale.US, "%.0f m", meters)
            } else {
                String.format(Locale.US, "%.2f km", meters / 1000.0)
            }
        }
    }

    fun formatAltitude(meters: Double, imperial: Boolean): String {
        return if (imperial) {
            String.format(Locale.US, "%.0f ft", meters * 3.28084)
        } else {
            String.format(Locale.US, "%.0f m", meters)
        }
    }

    fun formatDmsCoordinates(latitude: Double, longitude: Double): String {
        val latStr = toDms(latitude, pos = "N", neg = "S")
        val lonStr = toDms(longitude, pos = "E", neg = "W")
        return "$latStr, $lonStr"
    }

    private fun toDms(coord: Double, pos: String, neg: String): String {
        val hemi = if (coord >= 0) pos else neg
        val absVal = abs(coord)
        val deg = absVal.toInt()
        val minFull = (absVal - deg) * 60.0
        val min = minFull.toInt()
        val sec = (minFull - min) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\" %s", deg, min, sec, hemi)
    }
}
