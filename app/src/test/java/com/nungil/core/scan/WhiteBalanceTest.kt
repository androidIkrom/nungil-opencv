package com.nungil.core.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WhiteBalanceTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun gainsOfMatchesGainsOnUniformPixels() {
        val pixels = IntArray(50) { rgb(128, 64, 192) }
        assertArrayEquals(WhiteBalance.gains(pixels), WhiteBalance.gainsOf(128f, 64f, 192f), 1e-4f)
    }

    @Test fun gainsOfClampsAndIsNeutralOnBlack() {
        assertArrayEquals(floatArrayOf(1f, 1f, 1f), WhiteBalance.gainsOf(0f, 0f, 0f), 0f)
        // Means 10, 200, 200: gray 136.67; red wants 13.67, clamped to MAX_GAIN; the others 0.683.
        val g = WhiteBalance.gainsOf(10f, 200f, 200f)
        assertArrayEquals(floatArrayOf(WhiteBalance.MAX_GAIN, 0.68333f, 0.68333f), g, 1e-4f)
    }
}
