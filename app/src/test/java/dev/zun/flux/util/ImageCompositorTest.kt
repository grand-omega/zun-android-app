package dev.zun.flux.util

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [mapRectToSource]/[fitRectInContainer] (feature 014 research.md Decision 3). */
class ImageCompositorTest {
    private val delta = 0.01f

    @Test
    fun sameAspectRatio_isAUniformScaleWithNoLetterboxing() {
        val container = IntSize(1000, 1000)
        val image = IntSize(500, 500)

        val fit = fitRectInContainer(container, image)

        assertEquals(Rect(0f, 0f, 1000f, 1000f), fit)
    }

    @Test
    fun widerContainerThanImage_letterboxesLeftAndRight() {
        val container = IntSize(1000, 500) // 2:1
        val image = IntSize(500, 500) // 1:1 — narrower than the container

        val fit = fitRectInContainer(container, image)

        // Image fills the full height (500), width scaled to match (500), centered horizontally.
        assertEquals(250f, fit.left, delta)
        assertEquals(0f, fit.top, delta)
        assertEquals(750f, fit.right, delta)
        assertEquals(500f, fit.bottom, delta)
    }

    @Test
    fun tallerContainerThanImage_letterboxesTopAndBottom() {
        val container = IntSize(500, 1000) // 1:2
        val image = IntSize(500, 500) // 1:1 — shorter than the container

        val fit = fitRectInContainer(container, image)

        assertEquals(0f, fit.left, delta)
        assertEquals(250f, fit.top, delta)
        assertEquals(500f, fit.right, delta)
        assertEquals(750f, fit.bottom, delta)
    }

    @Test
    fun containerCenter_alwaysMapsToImageCenter_regardlessOfLetterboxing() {
        val container = IntSize(1000, 500)
        val image = IntSize(2000, 1000) // same 2:1 aspect ratio, no letterboxing this time
        val letterboxedImage = IntSize(200, 200) // 1:1 — will be letterboxed

        val containerCenter = Rect(500f, 250f, 500f, 250f)

        val noLetterbox = mapRectToSource(container, image, containerCenter)
        assertEquals(1000f, noLetterbox.left, delta)
        assertEquals(500f, noLetterbox.top, delta)

        val withLetterbox = mapRectToSource(container, letterboxedImage, containerCenter)
        assertEquals(100f, withLetterbox.left, delta)
        assertEquals(100f, withLetterbox.top, delta)
    }

    @Test
    fun aPointInTheLetterboxArea_mapsOutsideTheImagesOwnBounds() {
        val container = IntSize(1000, 500) // 2:1
        val image = IntSize(500, 500) // 1:1 — letterboxed left/right (fit rect is x=[250,750])

        // A point in the left letterbox bar (container x=0, well left of the fit rect's x=250).
        val leftBarPoint = Rect(0f, 250f, 0f, 250f)

        val mapped = mapRectToSource(container, image, leftBarPoint)

        assertEquals(true, mapped.left < 0f)
    }

    @Test
    fun mapRectToSource_isTheInverseOfFitRectInContainer() {
        val container = IntSize(1200, 800)
        val image = IntSize(600, 900)
        val fit = fitRectInContainer(container, image)

        // Mapping the image's own full-fit rect back through mapRectToSource should recover
        // (approximately) the image's own full bounds, 0..width / 0..height.
        val recovered = mapRectToSource(container, image, fit)

        assertEquals(0f, recovered.left, delta)
        assertEquals(0f, recovered.top, delta)
        assertEquals(image.width.toFloat(), recovered.right, delta)
        assertEquals(image.height.toFloat(), recovered.bottom, delta)
    }
}
