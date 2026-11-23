package com.example.ui

import com.example.compass.expressiveShapeRadiusFactor
import com.example.data.ExpressiveDialShape
import com.example.level.computeLevelBubbleMorphFraction
import com.example.level.levelBubbleRadiusAt
import com.example.ui.components.snapNavDestinationIndex
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

        val flower0 = expressiveShapeRadiusFactor(ExpressiveDialShape.ROUNDED_FLOWER, 0.0)
        val geometric0 = expressiveShapeRadiusFactor(ExpressiveDialShape.GEOMETRIC, 0.0)
        assertTrue(kotlin.math.abs(flower0 - geometric0) > 0.05f)
    }

    @Test
    fun `floating nav snap clamps bounds and respects flick velocity`() {
        assertEquals(0, snapNavDestinationIndex(fractionalIndex = 0.3f, velocityPxPerSec = 0f, slotSizePx = 100f, count = 4))
        assertEquals(1, snapNavDestinationIndex(fractionalIndex = 1.2f, velocityPxPerSec = 0f, slotSizePx = 100f, count = 4))
        assertEquals(2, snapNavDestinationIndex(fractionalIndex = 1.7f, velocityPxPerSec = 0f, slotSizePx = 100f, count = 4))
        assertEquals(2, snapNavDestinationIndex(fractionalIndex = 1.15f, velocityPxPerSec = 420f, slotSizePx = 100f, count = 4))
        assertEquals(0, snapNavDestinationIndex(fractionalIndex = -0.4f, velocityPxPerSec = -900f, slotSizePx = 100f, count = 4))
        assertEquals(3, snapNavDestinationIndex(fractionalIndex = 3.4f, velocityPxPerSec = 900f, slotSizePx = 100f, count = 4))
    }

    @Test
    fun `level orientation bubble is circular at center and morphs smoothly near circumference`() {
        assertEquals(0f, computeLevelBubbleMorphFraction(0.0f), 0.0001f)
        assertEquals(0f, computeLevelBubbleMorphFraction(0.10f), 0.0001f)

        val midMorph = computeLevelBubbleMorphFraction(0.49f)
        assertTrue("Expected smooth fractional morph in middle, got $midMorph", midMorph in 0.25f..0.75f)

        assertEquals(1f, computeLevelBubbleMorphFraction(0.90f), 0.0001f)
        assertEquals(1f, computeLevelBubbleMorphFraction(1.25f), 0.0001f)

        // At center (morph=0): uniform circular radius for all angles
        val centerAngles = (0 until 12).map { (it * 30.0 * PI / 180.0).toFloat() }
        centerAngles.forEach { angle ->
            assertEquals(24f, levelBubbleRadiusAt(24f, morphFraction = 0f, thetaRad = angle, tiltDirectionRad = 0f), 0.001f)
        }

        // Near edge (morph=1) tilted toward angle 0: tip at angle 0 extends noticeably farther than sides (90°/180°)
        val apexR = levelBubbleRadiusAt(24f, morphFraction = 1f, thetaRad = 0f, tiltDirectionRad = 0f)
        val sideR = levelBubbleRadiusAt(24f, morphFraction = 1f, thetaRad = (PI / 2.0).toFloat(), tiltDirectionRad = 0f)
        val rearR = levelBubbleRadiusAt(24f, morphFraction = 1f, thetaRad = PI.toFloat(), tiltDirectionRad = 0f)
        assertTrue("Apex ($apexR) should exceed side ($sideR) and rear ($rearR)", apexR > sideR && apexR > rearR)
    }
}
