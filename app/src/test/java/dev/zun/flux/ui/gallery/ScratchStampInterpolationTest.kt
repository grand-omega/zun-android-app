package dev.zun.flux.ui.gallery

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [interpolateStampPoints] (feature 010): FR-003, spec Edge Cases (tap vs. drag). */
class ScratchStampInterpolationTest {
    @Test
    fun returnsEvenlySpacedPointsBetweenTwoDistinctPoints() {
        val points = interpolateStampPoints(Offset(0f, 0f), Offset(100f, 0f), spacing = 25f)

        assertEquals(4, points.size)
        assertEquals(Offset(25f, 0f), points[0])
        assertEquals(Offset(50f, 0f), points[1])
        assertEquals(Offset(75f, 0f), points[2])
        assertEquals(Offset(100f, 0f), points[3])
    }

    @Test
    fun aTapWithIdenticalFromAndToReturnsASinglePoint() {
        val tap = Offset(42f, 17f)
        val points = interpolateStampPoints(tap, tap, spacing = 10f)

        assertEquals(listOf(tap), points)
    }

    @Test
    fun twoPointsCloserThanSpacingStillReturnAtLeastTheEndpoint() {
        val points = interpolateStampPoints(Offset(0f, 0f), Offset(2f, 0f), spacing = 25f)

        assertTrue(points.isNotEmpty())
        assertEquals(Offset(2f, 0f), points.last())
    }

    @Test
    fun theEndpointIsAlwaysTheLastPointReturned() {
        val points = interpolateStampPoints(Offset(10f, 10f), Offset(130f, 10f), spacing = 30f)

        assertEquals(Offset(130f, 10f), points.last())
    }
}
