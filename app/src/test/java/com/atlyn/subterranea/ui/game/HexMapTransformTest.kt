package com.atlyn.subterranea.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase N unit tests for the pure geometry helpers used by [HexMap]'s
 * pinch-to-zoom + pan implementation.
 *
 * The XY-suffixed primitive variants ([clampOffsetXY], [screenToCanvasLocalXY])
 * are tested directly here so the tests don't need androidx.compose.ui.geometry
 * on the JVM unit-test classpath.
 */
class HexMapTransformTest {

    private val eps = 0.001f

    // --- clampScale ----------------------------------------------------------

    @Test
    fun `clampScale floors below min`() {
        assertEquals(HEX_MAP_MIN_SCALE, clampScale(0.1f), eps)
        assertEquals(HEX_MAP_MIN_SCALE, clampScale(-5f), eps)
    }

    @Test
    fun `clampScale ceilings above max`() {
        assertEquals(HEX_MAP_MAX_SCALE, clampScale(5f), eps)
        assertEquals(HEX_MAP_MAX_SCALE, clampScale(100f), eps)
    }

    @Test
    fun `clampScale passes through values in range`() {
        assertEquals(1f, clampScale(1f), eps)
        assertEquals(1.5f, clampScale(1.5f), eps)
        assertEquals(HEX_MAP_MIN_SCALE, clampScale(HEX_MAP_MIN_SCALE), eps)
        assertEquals(HEX_MAP_MAX_SCALE, clampScale(HEX_MAP_MAX_SCALE), eps)
    }

    // --- clampOffsetXY -------------------------------------------------------

    @Test
    fun `clampOffsetXY allows the origin at any scale`() {
        for (scale in floatArrayOf(0.7f, 1f, 2f, 3f)) {
            val (x, y) = clampOffsetXY(0f, 0f, scale, 1080, 1920)
            assertEquals(0f, x, eps)
            assertEquals(0f, y, eps)
        }
    }

    @Test
    fun `clampOffsetXY caps pan at scale 1`() {
        // |tx| <= W * (0.25 + 1/2) = 0.75 * W
        val (x, _) = clampOffsetXY(99999f, 0f, 1f, 1000, 1000)
        assertEquals(750f, x, eps)
        val (xn, _) = clampOffsetXY(-99999f, 0f, 1f, 1000, 1000)
        assertEquals(-750f, xn, eps)
    }

    @Test
    fun `clampOffsetXY allows larger pan when zoomed in`() {
        // At scale=3, |tx| <= W * (0.25 + 1.5) = 1.75 * W
        val (x, _) = clampOffsetXY(99999f, 0f, 3f, 1000, 1000)
        assertEquals(1750f, x, eps)
    }

    @Test
    fun `clampOffsetXY caps both axes independently`() {
        // W=1000 H=2000, scale=2 -> maxX = 1000*1.25=1250; maxY = 2000*1.25=2500
        val (x, y) = clampOffsetXY(99999f, -99999f, 2f, 1000, 2000)
        assertEquals(1250f, x, eps)
        assertEquals(-2500f, y, eps)
    }

    @Test
    fun `clampOffsetXY returns input when canvas size is zero`() {
        // Defensive: before onSizeChanged fires, canvasSize is (0,0) and we
        // shouldn't divide by zero or crush the offset to origin.
        val (x, y) = clampOffsetXY(123f, 456f, 1.5f, 0, 0)
        assertEquals(123f, x, eps)
        assertEquals(456f, y, eps)
    }

    // --- screenToCanvasLocalXY ----------------------------------------------

    @Test
    fun `screenToCanvasLocalXY identity at scale 1 zero offset`() {
        val (x, y) = screenToCanvasLocalXY(123f, 456f, 1f, 0f, 0f, 1080, 1920)
        assertEquals(123f, x, eps)
        assertEquals(456f, y, eps)
    }

    @Test
    fun `screenToCanvasLocalXY inverts scale around center`() {
        // canvas 1000x1000, scale=2, offset=0
        // The screen center (500,500) should map back to canvas center (500,500).
        val (cx, cy) = screenToCanvasLocalXY(500f, 500f, 2f, 0f, 0f, 1000, 1000)
        assertEquals(500f, cx, eps)
        assertEquals(500f, cy, eps)

        // Screen point (1000,1000) under 2x zoom around center came from
        // canvas point (cx + 500/2 = 750, 750).
        val (lx, ly) = screenToCanvasLocalXY(1000f, 1000f, 2f, 0f, 0f, 1000, 1000)
        assertEquals(750f, lx, eps)
        assertEquals(750f, ly, eps)
    }

    @Test
    fun `screenToCanvasLocalXY inverts pan`() {
        // canvas 1000x1000, scale=1, offset=(100,200).
        // The drawn content is shifted right by 100 and down by 200 in screen
        // space, so a tap at screen (300,300) corresponds to canvas (200,100).
        val (lx, ly) = screenToCanvasLocalXY(300f, 300f, 1f, 100f, 200f, 1000, 1000)
        assertEquals(200f, lx, eps)
        assertEquals(100f, ly, eps)
    }

    @Test
    fun `screenToCanvasLocalXY round-trips with the forward transform`() {
        // For several (scale, offset) settings, applying the forward transform
        // and then the inverse should give back the original local point.
        val w = 1080
        val h = 1920
        val cases = listOf(
            Triple(1f, 0f, 0f),
            Triple(0.7f, -50f, 200f),
            Triple(2f, 250f, -150f),
            Triple(3f, 800f, 400f)
        )
        val localPoints = listOf(
            300f to 200f,
            540f to 960f, // exact center
            900f to 1500f
        )
        for ((scale, offX, offY) in cases) {
            for ((localX, localY) in localPoints) {
                val (sx, sy) = canvasLocalToScreenXY(localX, localY, scale, offX, offY, w, h)
                val (rx, ry) = screenToCanvasLocalXY(sx, sy, scale, offX, offY, w, h)
                assertEquals("scale=$scale off=($offX,$offY) localX", localX, rx, 0.01f)
                assertEquals("scale=$scale off=($offX,$offY) localY", localY, ry, 0.01f)
            }
        }
    }

    @Test
    fun `screenToCanvasLocalXY returns input when scale is zero`() {
        // Defensive: never divide by zero — degenerate scale just falls through.
        val (x, y) = screenToCanvasLocalXY(42f, 84f, 0f, 0f, 0f, 1000, 1000)
        assertEquals(42f, x, eps)
        assertEquals(84f, y, eps)
    }

    /** The forward transform that the graphicsLayer applies (center origin). */
    private fun canvasLocalToScreenXY(
        localX: Float,
        localY: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        canvasWidth: Int,
        canvasHeight: Int
    ): Pair<Float, Float> {
        val cx = canvasWidth / 2f
        val cy = canvasHeight / 2f
        val sx = (localX - cx) * scale + cx + offsetX
        val sy = (localY - cy) * scale + cy + offsetY
        return sx to sy
    }

    @Test
    fun `min and max scale constants are sensible`() {
        assertTrue("min < 1", HEX_MAP_MIN_SCALE < 1f)
        assertTrue("max > 1", HEX_MAP_MAX_SCALE > 1f)
        assertTrue("min > 0", HEX_MAP_MIN_SCALE > 0f)
    }
}
