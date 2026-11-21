package com.example.ui

import com.example.compass.expressiveShapeRadiusFactor
import com.example.data.ExpressiveDialShape
import com.example.ui.components.computeNavIndicatorEdgeMorph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class CompassDialShapesAndNavMathTest {

    @Test
    fun `expressive dial shapes produce bounded and distinct radial profiles`() {
        val sampleAngles = (0 until 72).map { (it * 5.0) * PI / 180.0 }
        val profileStats = mutableMapOf<ExpressiveDialShape, Pair<Float, Float>>()

        ExpressiveDialShape.entries.forEach { shape ->
            val radii = sampleAngles.map { theta -> expressiveShapeRadiusFactor(shape, theta) }
            val minR = radii.minOrNull() ?: 0f
            val maxR = radii.maxOrNull() ?: 0f
            assertTrue("Shape $shape minR=$minR out of bounds", minR >= 0.84f)
            assertTrue("Shape $shape maxR=$maxR out of bounds", maxR <= 1.02f)
            profileStats[shape] = minR to maxR
        }

        val circleRange = profileStats.getValue(ExpressiveDialShape.CIRCLE)
        assertEquals(0f, circleRange.second - circleRange.first, 0.0001f)

        val scallopedRange = profileStats.getValue(ExpressiveDialShape.SOFT_SCALLOPED)
        assertTrue(scallopedRange.second - scallopedRange.first > 0.05f)

        val flowerRange = profileStats.getValue(ExpressiveDialShape.ROUNDED_FLOWER)
        assertTrue(flowerRange.second - flowerRange.first > 0.07f)

        val waveRange = profileStats.getValue(ExpressiveDialShape.ORGANIC_WAVE)
        assertTrue(waveRange.second - waveRange.first > 0.05f)

        val geometricRange = profileStats.getValue(ExpressiveDialShape.GEOMETRIC)
        assertTrue(geometricRange.second - geometricRange.first > 0.01f)

        // Ensure Flower (8-petal) and Geometric (12-facet) differ clearly at 0 degrees
        val flower0 = expressiveShapeRadiusFactor(ExpressiveDialShape.ROUNDED_FLOWER, 0.0)
        val geometric0 = expressiveShapeRadiusFactor(ExpressiveDialShape.GEOMETRIC, 0.0)
        assertTrue(kotlin.math.abs(flower0 - geometric0) > 0.05f)
    }

    @Test
    fun `morphing nav indicator is circular at central tabs and triangular near edges`() {
        // 4 navigation tabs (0=Compass, 1=Level, 2=ArcPark, 3=Settings)
        val leftEdge = computeNavIndicatorEdgeMorph(animatedIndex = 0f, count = 4)
        val centerLeft = computeNavIndicatorEdgeMorph(animatedIndex = 1f, count = 4)
        val centerRight = computeNavIndicatorEdgeMorph(animatedIndex = 2f, count = 4)
        val rightEdge = computeNavIndicatorEdgeMorph(animatedIndex = 3f, count = 4)

        assertEquals(1f, leftEdge, 0.001f)
        assertEquals(0f, centerLeft, 0.001f)
        assertEquals(0f, centerRight, 0.001f)
        assertEquals(1f, rightEdge, 0.001f)

        // Mid-transition between center (1f) and edge (0f) is strictly fractional
        val midMorph = computeNavIndicatorEdgeMorph(animatedIndex = 0.25f, count = 4)
        assertTrue("Expected fractional edge morph at 0.25, got $midMorph", midMorph in 0.1f..0.9f)
    }
}
