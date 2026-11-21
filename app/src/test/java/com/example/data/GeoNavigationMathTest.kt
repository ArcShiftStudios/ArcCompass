package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoNavigationMathTest {

    @Test
    fun `normalizeDegrees wraps negative and over-360 angles`() {
        assertEquals(350f, GeoNavigationMath.normalizeDegrees(-10f), 0.01f)
        assertEquals(15f, GeoNavigationMath.normalizeDegrees(375f), 0.01f)
        assertEquals(0f, GeoNavigationMath.normalizeDegrees(360f), 0.01f)
    }

    @Test
    fun `shortestSignedDelta crosses north seam cleanly`() {
        assertEquals(10f, GeoNavigationMath.shortestSignedDelta(355f, 5f), 0.01f)
        assertEquals(-10f, GeoNavigationMath.shortestSignedDelta(5f, 355f), 0.01f)
    }

    @Test
    fun `cardinals and quadrant bearing match precision conventions`() {
        assertEquals("N", GeoNavigationMath.cardinal16(0f))
        assertEquals("N", GeoNavigationMath.cardinal16(358f))
        assertEquals("NE", GeoNavigationMath.cardinal16(45f))
        assertEquals("SSW", GeoNavigationMath.cardinal16(202f))
        assertEquals("NW", GeoNavigationMath.cardinal8(315f))

        assertEquals("N", GeoNavigationMath.formatQuadrantBearing(0f))
        assertEquals("S 68° E", GeoNavigationMath.formatQuadrantBearing(112f))
        assertEquals("N 45° W", GeoNavigationMath.formatQuadrantBearing(315f))
    }

    @Test
    fun `haversine distance and initial bearing are accurate`() {
        // ~111.19 km per degree latitude
        val dist = GeoNavigationMath.haversineDistanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(dist in 110_000.0..112_000.0)

        val northBearing = GeoNavigationMath.initialBearingDegrees(37.0, -122.0, 38.0, -122.0)
        assertEquals(0f, northBearing, 0.5f)

        val eastBearing = GeoNavigationMath.initialBearingDegrees(0.0, 0.0, 0.0, 1.0)
        assertEquals(90f, eastBearing, 0.5f)
    }

    @Test
    fun `relative turn and direction prompt reflect phone-to-target orientation`() {
        assertEquals(45f, GeoNavigationMath.relativeTurnDegrees(45f, 0f), 0.01f)
        assertEquals(-45f, GeoNavigationMath.relativeTurnDegrees(315f, 0f), 0.01f)

        assertEquals("Near your parking spot", GeoNavigationMath.relativeDirectionPrompt(90f, 5.0))
        assertEquals("Straight ahead", GeoNavigationMath.relativeDirectionPrompt(5f, 40.0))
        assertEquals("Slightly right", GeoNavigationMath.relativeDirectionPrompt(30f, 40.0))
        assertEquals("To your left", GeoNavigationMath.relativeDirectionPrompt(-85f, 40.0))
        assertEquals("Behind you", GeoNavigationMath.relativeDirectionPrompt(165f, 40.0))
    }

    @Test
    fun `distance, altitude, and dms formatting match units`() {
        assertEquals("140 m", GeoNavigationMath.formatDistance(140.0, imperial = false))
        assertEquals("1.50 km", GeoNavigationMath.formatDistance(1500.0, imperial = false))
        assertEquals("328 ft", GeoNavigationMath.formatDistance(100.0, imperial = true))
        assertEquals("920 m", GeoNavigationMath.formatAltitude(920.0, imperial = false))

        val dms = GeoNavigationMath.formatDmsCoordinates(37.7749, -122.4194)
        assertTrue(dms.contains("N") && dms.contains("W"))
    }
}
